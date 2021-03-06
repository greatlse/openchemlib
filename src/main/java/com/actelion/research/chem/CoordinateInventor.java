/*
* Copyright (c) 1997 - 2016
* Actelion Pharmaceuticals Ltd.
* Gewerbestrasse 16
* CH-4123 Allschwil, Switzerland
*
* All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* 1. Redistributions of source code must retain the above copyright notice, this
*    list of conditions and the following disclaimer.
* 2. Redistributions in binary form must reproduce the above copyright notice,
*    this list of conditions and the following disclaimer in the documentation
*    and/or other materials provided with the distribution.
* 3. Neither the name of the the copyright holder nor the
*    names of its contributors may be used to endorse or promote products
*    derived from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
* ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
* WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
* ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
* (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
* LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
* ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
* (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
* SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*
*/

package com.actelion.research.chem;

import java.util.*;

public class CoordinateInventor {
	public static final int MODE_REMOVE_HYDROGEN = 1;
	public static final int MODE_KEEP_MARKED_ATOM_COORDS = 2;
	public static final int MODE_PREFER_MARKED_ATOM_COORDS = 4;
	private static final int MODE_CONSIDER_MARKED_ATOMS = MODE_KEEP_MARKED_ATOM_COORDS | MODE_PREFER_MARKED_ATOM_COORDS;

	private static final byte FLIP_AS_LAST_RESORT = 1;
	private static final byte FLIP_POSSIBLE = 2;
	private static final byte FLIP_PREFERRED = 3;
	private static final int  PREFERRED_FLIPS = 32;
	private static final int  POSSIBLE_FLIPS = 64;
	private static final int  LAST_RESORT_FLIPS = 128;
	private static final int  TOTAL_FLIPS = PREFERRED_FLIPS + POSSIBLE_FLIPS + LAST_RESORT_FLIPS;

	private StereoMolecule mMol;
	private ArrayList<InventorFragment>   mFragmentList;
	private Random		mRandom;
	private boolean[]	mAtomHandled;
	private boolean[]	mBondHandled;
	private int			mMode,mAtoms,mBonds;
	private int[]		mConnAtoms;

	/**
	 * Creates an CoordinateInventor, which removes unneeded hydrogen atoms
	 * and creates new atom coordinates for all(!) atoms.
	 */
	public CoordinateInventor () {
		mMode = MODE_REMOVE_HYDROGEN;
		}


	/**
	 * Creates an CoordinateInventor, which removes unneeded hydrogens, if mode flags include
	 * MODE_REMOVE_HYDROGEN. If mode includes MODE_KEEP_MARKED_ATOM_COORDS, then marked atoms
	 * keep their coordinates. If mode includes MODE_PREFER_MARKED_ATOM_COORDS, then coordinates
	 * of marked atoms are changed only, if perfect coordinates are not possible without.
	 * @param mode
	 */
	public CoordinateInventor (int mode) {
		mMode = mode;
		}


	public void setRandomSeed(long seed) {
		mRandom = new Random(seed);
		}


	/**
	 * Creates new atom 2D-coordinates for a molecule or a part of a molecule.
	 * Coordinates will correctly reflect E/Z double bond parities, unless the double bond is in a ring.
	 * If atom parities are available, this call is typically followed by a calling mol.setStereoBondsFromParity();
	 * Unneeded explicit hydrogens are removed, if mode includes MODE_REMOVE_HYDROGEN.
	 * The relative orientation of all marked atoms is retained, if mode includes MODE_KEEP_MARKED_ATOM_COORDS.
	 * The relative orientation of all marked atoms is changed as last resort only, if mode includes MODE_PREFER_MARKED_ATOM_COORDS.
	 * @param mol the molecule that gets new 2D coordinates in place
	 */
	public void invent(StereoMolecule mol) {
		if (mRandom == null)
			mRandom = new Random();

		mMol = mol;
		mMol.ensureHelperArrays(Molecule.cHelperRings);

		if ((mMode & MODE_REMOVE_HYDROGEN) == 0) {
			mAtoms = mMol.getAllAtoms();
			mBonds = mMol.getAllBonds();
			mConnAtoms = new int[mAtoms];
			for (int atom=0; atom<mAtoms; atom++)
				mConnAtoms[atom] = mMol.getAllConnAtoms(atom);
			}
		else {
			mAtoms = mMol.getAtoms();
			mBonds = mMol.getBonds();
			mConnAtoms = new int[mAtoms];
			for (int atom=0; atom<mAtoms; atom++)
				mConnAtoms[atom] = mMol.getConnAtoms(atom);
			}

		mFragmentList = new ArrayList<InventorFragment>();
		mAtomHandled = new boolean[mAtoms];
		mBondHandled = new boolean[mBonds];

		if ((mMode & MODE_CONSIDER_MARKED_ATOMS) != 0)
			locateCoreFragment();

		locateInitialFragments();
		joinOverlappingFragments();

		locateChainFragments();
		joinOverlappingFragments();

		locateFragmentBonds();
		correctChainEZParities();
		optimizeFragments();

		locateSingleAtoms();

		arrangeAllFragments();

		for (int i=0; i<mFragmentList.size(); i++) {
			InventorFragment f = mFragmentList.get(i);
			for (int j=0; j<f.size(); j++) {
				mMol.setAtomX(f.mAtom[j], f.mAtomX[j]);
				mMol.setAtomY(f.mAtom[j], f.mAtomY[j]);
				mMol.setAtomZ(f.mAtom[j], 0.0);
				}
			}

		if ((mMode & MODE_REMOVE_HYDROGEN) != 0) {
			if (mAtoms != 0) {	// otherwise it is H2, which shall be kept
				mMol.setAllAtoms(mAtoms);	// get rid of useless hydrogens
				mMol.setAllBonds(mBonds);
				}
			}
		}


	private void locateCoreFragment() {
			// take every small ring whose atoms are not a superset of another small ring
		int bondCount = 0;
		double avbl = 0;
		for (int bond=0; bond<mBonds; bond++) {
			if (mMol.isMarkedAtom(mMol.getBondAtom(0, bond))
			 && mMol.isMarkedAtom(mMol.getBondAtom(1, bond))) {
				mBondHandled[bond] = true;
				avbl += mMol.getBondLength(bond);
				bondCount++;
				}
			}
		if (bondCount == 0 || avbl == 0.0)
			return;
		avbl /= bondCount;

		for (int atom=0; atom<mAtoms; atom++) {
			if (mMol.isMarkedAtom(atom)) {
				if (mConnAtoms[atom] == 0)
					mMol.setAtomMarker(atom, false);
				else
					mAtomHandled[atom] = true;
				}
			}

		int[] fragmentNo = new int[mAtoms];
		int coreFragmentCount = mMol.getFragmentNumbers(fragmentNo, true);

		int[] fragmentAtomCount = new int[coreFragmentCount];
		for (int atom=0; atom<mAtoms; atom++)
			if (fragmentNo[atom] != -1)
				fragmentAtomCount[fragmentNo[atom]]++;

		InventorFragment[] fragment = new InventorFragment[coreFragmentCount];
		for (int f=0; f<coreFragmentCount; f++)
			fragment[f] = new InventorFragment(mMol, fragmentAtomCount[f]);

		int[] atomIndex = new int[coreFragmentCount];
		for (int atom=0; atom<mAtoms; atom++) {
			int f = fragmentNo[atom];
			if (f != -1) {
				fragment[f].mPriority[atomIndex[f]] = 256;
				fragment[f].mAtom[atomIndex[f]] = atom;
				fragment[f].mAtomX[atomIndex[f]] = mMol.getAtomX(atom) / avbl;
				fragment[f].mAtomY[atomIndex[f]] = mMol.getAtomY(atom) / avbl;
				atomIndex[f]++;
				}
			}

			// Find the largest core fragment and retain its orientation
			// by adding it first to the fragment list 
		int maxFragment = -1;
		int maxFragmentAtoms = 0;
		for (int f=0; f<coreFragmentCount; f++) {
			if (maxFragmentAtoms < fragmentAtomCount[f]) {
				maxFragmentAtoms = fragmentAtomCount[f];
				maxFragment = f;
				}
			}
			
		mFragmentList.add(fragment[maxFragment]);
		for (int f=0; f<coreFragmentCount; f++)
			if (f != maxFragment)
				mFragmentList.add(fragment[f]);
		}


	private void locateInitialFragments() {
			// take every atom with more than 4 neighbours including first neighbour shell
		for (int atom=0; atom<mAtoms; atom++) {
			if (mConnAtoms[atom] > 4) {
				InventorFragment f = new InventorFragment(mMol, 1+mConnAtoms[atom]);

				f.mAtomX[mConnAtoms[atom]] = 0.0;
				f.mAtomY[mConnAtoms[atom]] = 0.0;
				f.mPriority[mConnAtoms[atom]] = 32;
				f.mAtom[mConnAtoms[atom]] = atom;
				mAtomHandled[atom] = true;

				for (int i=0; i<mConnAtoms[atom]; i++) {
					int connAtom = mMol.getConnAtom(atom, i);
					f.mAtomX[i] = Math.sin(Math.PI/3*i-Math.PI/3*2);
					f.mAtomY[i] = Math.cos(Math.PI/3*i-Math.PI/3*2);
					f.mPriority[i] = 32;
					f.mAtom[i] = connAtom;
					mAtomHandled[connAtom] = true;
					mBondHandled[mMol.getConnBond(atom, i)] = true;
					}

				mFragmentList.add(f);
				}
			}


			// take every small ring whose atoms are not a superset of another small ring
		RingCollection ringSet = mMol.getRingSet();
		for (int ringNo=0; ringNo<ringSet.getSize(); ringNo++) {
			int ringSize = ringSet.getRingSize(ringNo);
			int[] ringAtom = ringSet.getRingAtoms(ringNo);

				// skip rings that are entirely in the core fragment, if retainCore is true
			boolean skipRing = false;
			if ((mMode & MODE_CONSIDER_MARKED_ATOMS) != 0) {
				skipRing = true;
				for (int i=0; i<ringSize; i++) {
					if (!mMol.isMarkedAtom(ringAtom[i])) {
						skipRing = false;
						break;
						}
					}
				}
			
			if (!skipRing) {
				boolean isElementaryRing = false;
				for (int i=0; i<ringSize; i++) {
					if (mMol.getAtomRingSize(ringAtom[i]) == ringSize) {
						isElementaryRing = true;
						break;
						}
					}
				if (isElementaryRing) {
					int[] ringBond = ringSet.getRingBonds(ringNo);
	
					addRingFragment(ringAtom, ringBond);
	
					for (int i=0; i<ringSize; i++) {
						mAtomHandled[ringAtom[i]] = true;
						mBondHandled[ringBond[i]] = true;
						}
					}
				}
			}

			// take every large ring that has ring bonds that are not member of a fragment added already
		for (int bond=0; bond<mBonds; bond++) {
			if (mMol.isRingBond(bond) && !mBondHandled[bond]) {
				InventorChain theRing = getSmallestRingFromBond(bond);
				int[] ringAtom = theRing.getRingAtoms();
				int[] ringBond = theRing.getRingBonds();
				addRingFragment(ringAtom, ringBond);

				for (int i=0; i<theRing.getChainLength(); i++) {
					mAtomHandled[ringAtom[i]] = true;
					mBondHandled[ringBond[i]] = true;
					}
				}
			}

			// take every triple bond including first level attached atoms
		for (int bond=0; bond<mBonds; bond++) {
			if (!mBondHandled[bond] && mMol.getBondOrder(bond) == 3) {
				int atom1 = mMol.getBondAtom(0, bond);
				int atom2 = mMol.getBondAtom(1, bond);
				int members = mConnAtoms[atom1] + mConnAtoms[atom2];
				if (members > 2) {
					InventorFragment f = new InventorFragment(mMol, members);
					int count = 0;
					for (int i=0; i<mConnAtoms[atom1]; i++) {
						int connAtom = mMol.getConnAtom(atom1, i);
						if (connAtom != atom2) {
							f.mAtom[count++] = connAtom;
							mAtomHandled[connAtom] = true;
							mBondHandled[mMol.getConnBond(atom1, i)] = true;
							}
						}
					f.mAtom[count++] = atom1;
					f.mAtom[count++] = atom2;
					for (int i=0; i<mConnAtoms[atom2]; i++) {
						int connAtom = mMol.getConnAtom(atom2, i);
						if (connAtom != atom1) {
							f.mAtom[count++] = connAtom;
							mAtomHandled[connAtom] = true;
							mBondHandled[mMol.getConnBond(atom2, i)] = true;
							}
						}
					for (int i=0; i<members; i++) {
						f.mAtomX[i] = (double)i;
						f.mAtomY[i] = 0.0;
						f.mPriority[i] = 1;
						}
					mAtomHandled[atom1] = true;
					mAtomHandled[atom2] = true;
					mBondHandled[bond] = true;
					mFragmentList.add(f);
					}
				}
			}

			// take cumulated double bonds including first level single bonded atoms
		for (int bond=0; bond<mBonds; bond++) {
			if (!mBondHandled[bond] && mMol.getBondOrder(bond) == 2) {
				int[] alleneAtom = new int[mAtoms];
				for (int i=0; i<2; i++) {
					alleneAtom[0] = mMol.getBondAtom(i, bond);
					alleneAtom[1] = mMol.getBondAtom(1-i, bond);
					if (mMol.getAtomPi(alleneAtom[0]) == 1
					 && mMol.getAtomPi(alleneAtom[1]) == 2
					 && mConnAtoms[alleneAtom[1]] == 2) { // found start of cumulated double bonds
						mAtomHandled[alleneAtom[0]] = true;
						mAtomHandled[alleneAtom[1]] = true;
						mBondHandled[bond] = true;
						int last = 1;
						do {
							int nextIndex = (mMol.getConnAtom(alleneAtom[last], 0)
											 == alleneAtom[last-1]) ? 1 : 0;
							alleneAtom[last+1] = mMol.getConnAtom(alleneAtom[last], nextIndex);

								// stop at centers like C=Cr(Rn)=N
							if (mMol.getAtomPi(alleneAtom[last+1]) == 2
							 && mConnAtoms[alleneAtom[last+1]] > 2)
								break;

							mAtomHandled[alleneAtom[last+1]] = true;
							mBondHandled[mMol.getConnBond(alleneAtom[last], nextIndex)] = true;
							last++;
							} while (mMol.getAtomPi(alleneAtom[last]) == 2
								  && mConnAtoms[alleneAtom[last]] == 2);

						int members = mConnAtoms[alleneAtom[0]]
									+ mConnAtoms[alleneAtom[last]]
									+ last - 1;
						InventorFragment f = new InventorFragment(mMol, members);
						for (int j=0; j<=last; j++) {
							f.mAtomX[j] = (double)j;
							f.mAtomY[j] = 0.0;
							f.mPriority[j] = 64;
							f.mAtom[j] = alleneAtom[j];
							}

						int current = last+1;
						boolean found = false;
						for (int j=0; j<mConnAtoms[alleneAtom[0]]; j++) {
							int connAtom = mMol.getConnAtom(alleneAtom[0], j);
							if (connAtom != alleneAtom[1]) {
								f.mAtomX[current] = -0.5;
								f.mAtomY[current] = (found) ? Math.sin(Math.PI/3) : -Math.sin(Math.PI/3);
								f.mPriority[current] = 64;
								f.mAtom[current] = connAtom;
								current++;
								found = true;
								}
							}

						found = false;
						for (int j=0; j<mConnAtoms[alleneAtom[last]]; j++) {
							int connAtom = mMol.getConnAtom(alleneAtom[last], j);
							if (connAtom != alleneAtom[last-1]) {
								f.mAtomX[current] = (double)last + 0.5;
								f.mAtomY[current] = (found) ? -Math.sin(Math.PI/3) : Math.sin(Math.PI/3);
								f.mPriority[current] = 64;
								f.mAtom[current] = connAtom;
								current++;
								found = true;
								}
							}

						mFragmentList.add(f);
						}
					}
				}
			}

			// predefine quartary centers with exactly 2 not further subtituted substituents
		for (int atom=0; atom<mAtoms; atom++) {
			if (mConnAtoms[atom] == 4) {
				int[] primaryConnAtom = new int[4];
				int[] primaryConnBond = new int[4];
				int primaryConns = 0;
				for (int i=0; i<4; i++) {
					primaryConnAtom[primaryConns] = mMol.getConnAtom(atom, i);
					primaryConnBond[primaryConns] = mMol.getConnBond(atom, i);
					if (mConnAtoms[primaryConnAtom[primaryConns]] == 1
					 && !mBondHandled[primaryConnBond[primaryConns]])
						primaryConns++;
					}

				if (primaryConns == 2) {
//					mAtomHandled[atom] = true;	don't break zig-zag of chains that are handled later
					InventorFragment f = new InventorFragment(mMol, 3);
					for (int i=0; i<2; i++) {
						mAtomHandled[primaryConnAtom[i]] = true;
						mBondHandled[primaryConnBond[i]] = true;
						f.mAtom[i] = primaryConnAtom[i];
						f.mPriority[i] = 32;
						}

					f.mAtomX[0] = -0.5;
					f.mAtomY[0] = 0.866;
					f.mAtomX[1] = 0.5;
					f.mAtomY[1] = 0.866;

					f.mAtomX[2] = 0.0;
					f.mAtomY[2] = 0.0;
					f.mPriority[2] = 32;
					f.mAtom[2] = atom;

					mFragmentList.add(f);
					}
				if (primaryConns == 3) {
					// if there is a single bond make sure that primaryConnBond[2] is one
					for (int i=0; i<2; i++) {
						if (mMol.getBondOrder(primaryConnBond[i]) == 1) {
							int temp = primaryConnAtom[i];
							primaryConnAtom[i] = primaryConnAtom[2];
							primaryConnAtom[2] = temp;
							temp = primaryConnBond[i];
							primaryConnBond[i] = primaryConnBond[2];
							primaryConnBond[2] = temp;
							}
						}

//					mAtomHandled[atom] = true;	don't break zig-zag of chains that are handled later
					InventorFragment f = new InventorFragment(mMol, 4);
					for (int i=0; i<3; i++) {
						mAtomHandled[primaryConnAtom[i]] = true;
						mBondHandled[primaryConnBond[i]] = true;
						f.mAtom[i] = primaryConnAtom[i];
						f.mPriority[i] = 32;
						}

					f.mAtomX[0] = -1.0;
					f.mAtomY[0] = 0.0;
					f.mAtomX[1] = 1.0;
					f.mAtomY[1] = 0.0;
					f.mAtomX[2] = 0.0;
					f.mAtomY[2] = 1.0;

					f.mAtomX[3] = 0.0;
					f.mAtomY[3] = 0.0;
					f.mPriority[3] = 32;
					f.mAtom[3] = atom;

					mFragmentList.add(f);
					}
				}
			}

/*	The current implementation does not (!!!) retain E/Z geometry of double bonds in a ring...
	Use the following to create E/Z fragments of ring double bonds with E/Z geometry reflecting
	coordinates. Retaining Reliably E/Z geometries, however, will need in addition:
	- consider these fragments coordinates when joining even if all fragments atom are already
	  part of the other fragment
	- a more capable joining algorithm that prevents E/Z inversions of double bonds by relocating
	  atoms that are part of the joint fragment

			// take stero defined ring-double-bonds including first level attached atoms
		for (int bond=0; bond<mBonds; bond++) {
			int bondParity = mMol.getBondParity(bond);
			if (mMol.isRingBond(bond)
			 && (bondParity == Molecule.cBondParityE
			  || bondParity == Molecule.cBondParityZ)) {
			 	int[] bondAtom = new int[2];
				bondAtom[0] = mMol.getBondAtom(0, bond);
				bondAtom[1] = mMol.getBondAtom(1, bond);
				int members = mConnAtoms[bondAtom[0]] + mConnAtoms[bondAtom[1]];

				InventorFragment f = new InventorFragment(mMol, members);
				int count = 0;
				boolean[] secondAtomCounts = new boolean[2];
				for (int i=0; i<2; i++) {
					mAtomHandled[bondAtom[i]] = true;
					f.mAtomX[count] = (double)i-0.5;
					f.mAtomY[count] = 0.0;
					f.mPriority[count] = 128;
					f.mAtom[count++] = bondAtom[i];
					int neighbours = 0;
					for (int j=0; j<mConnAtoms[bondAtom[i]]; j++) {
						int connAtom = mMol.getConnAtom(bondAtom[i], j);
						if (connAtom != bondAtom[1-i]) {
							if (neighbours == 1 && f.mAtom[count-1] > connAtom)
								secondAtomCounts[i] = true;
							f.mAtomX[count] = (i==0) ? -1.0 : 1.0;
							f.mAtomY[count] = (neighbours==0) ? -Math.sin(Math.PI/3) : Math.sin(Math.PI/3);
							f.mAtom[count++] = connAtom;
							mAtomHandled[connAtom] = true;
							mBondHandled[mMol.getConnBond(bondAtom[i], j)] = true;
							neighbours++;
							}
						}
					}

				if ((bondParity == Molecule.cBondParityE) ^ (secondAtomCounts[0] ^ secondAtomCounts[1]))
					for (int i=1; i<mConnAtoms[mMol.getBondAtom(0, bond)]; i++)
						f.mAtomY[i] *= -1.0;

				mBondHandled[bond] = true;
				mFragmentList.addElement(f);
				}
			}	*/
		}


	private void locateChainFragments() {
		while (true) {
			InventorChain longestChain = null;

			for (int atom=0; atom<mAtoms; atom++) {
				int unhandledBonds = 0;
				for (int i=0; i<mConnAtoms[atom]; i++)
					if (!mBondHandled[mMol.getConnBond(atom, i)])
						unhandledBonds++;

				if (unhandledBonds == 1) {
					InventorChain theChain = getLongestUnhandledChain(atom);
					if (longestChain == null
					 || theChain.getChainLength() > longestChain.getChainLength())
						longestChain = theChain;
					}
				}

			if (longestChain == null)
				break;

			InventorFragment f = new InventorFragment(mMol, longestChain.getChainLength());
			for (int i=0; i<longestChain.getChainLength(); i++) {
				mAtomHandled[longestChain.mAtom[i]] = true;
				if (i < longestChain.getChainLength() - 1)
					mBondHandled[longestChain.mBond[i]] = true;
				f.mAtom[i] = longestChain.mAtom[i];
				f.mAtomX[i] = Math.cos(Math.PI / 6) * i;
				f.mAtomY[i] = ((i & 1) == 1) ? 0.0 : 0.5;
				f.mPriority[i] = 128 + longestChain.getChainLength();
				}
			mFragmentList.add(f);
			}
		}


	private void locateSingleAtoms() {
		for (int atom=0; atom<mAtoms; atom++) {
			if (mConnAtoms[atom] == 0) {
				InventorFragment f = new InventorFragment(mMol, 1);
				mAtomHandled[atom] = true;
				f.mAtom[0] = atom;
				f.mAtomX[0] = 0.0;
				f.mAtomY[0] = 0.0;
				f.mPriority[0] = 0;
				mFragmentList.add(f);
				}
			}
		}


	private void addRingFragment(int[] ringAtom, int[] ringBond) {
		int ringSize = ringAtom.length;
		InventorFragment f = new InventorFragment(mMol, ringSize);
		f.mAtomX[0] = 0.0;
		f.mAtomY[0] = 0.0;
		for (int i=0; i<ringSize; i++) {
			f.mPriority[i] = 128 - ringSize;
			f.mAtom[i] = ringAtom[i];
			}

		if (ringSize < 8)
			createRegularRingFragment(f);
		else  // create a large ring considering E-double bonds and other constraints
			createLargeRingFragment(f, ringAtom, ringBond);

		mFragmentList.add(f);
		}

	
	private void createRegularRingFragment(InventorFragment f) {
		double angleChange = Math.PI - (Math.PI * (f.size()-2))/f.size();
		for (int i=1; i<f.size(); i++) {
			f.mAtomX[i] = f.mAtomX[i-1] + Math.sin(angleChange*(i-1));
			f.mAtomY[i] = f.mAtomY[i-1] + Math.cos(angleChange*(i-1));
			}
		}

	private void createLargeRingFragment(InventorFragment f, int[] ringAtom, int[] ringBond) {
		final int[][] cBondZList = { // sequence of E/Z parities in rings (E=0, Z=1)
				{   // 10-membered ring
					0x00000273,  // 1001110011 sym
				},
				null,
				{   // 12-membered rings
					0x00000999   // 100110011001 sym
				},
				null,
				{   // 14-membered rings
					0x00000993,  // 00100110010011 sym
					0x000021C3,  // 10000111000011 sym
					0x000009D7   // 00100111010111 sym
				},
				null,
				{   // 16-membered rings
					0x00008649,  // 1000011001001001 sym
					0x80008759   // 1000011101011001 asy
				},
				null,
				{   // 18-membered rings
					0x00009249,  // 001001001001001001 sym
					0x00021861,  // 100001100001100001 sym
					0x000175D7,  // 010111010111010111 sym
					0x00008643,  // 001000011001000011 sym
					0x000093B7,  // 001001001110110111 sym
					0x0000D66B,  // 001101011001101011 sym
					0x00020703,  // 100000011100000011 sym
					0x8002A753,  // 101010011101010011 asy
					0x0000D649,  // 001101011001001001 sym
					0x0000D759,  // 001101011101011001 sym 
					0x80008753,  // 001000011101010011 asy
					0x80008717   // 001000011100010111 asy
				},
				null,
				{   // 20-membered rings
					0x00081909,  // 10000001100100001001 sym
					0x00081D6B,  // 10000001110101101011 sym
					0x000DB861,  // 11011011100001100001 sym
					0x00021849,  // 00100001100001001001 sym
					0x000A9959,  // 10101001100101011001 sym
					0x80081D49,  // 10000001110101001001 asy
					0x800819A3,  // 10000001100110100011 asy 
					0x80084ED9,  // 10000100111011011001 asy
					0x80087475,  // 10000111010001110101 asy 
					0x80087464,  // 10000111010001100100 asy
					0x800D19A9,  // 11010001100110101001 asy 
					0x80086BA9,  // 10000110101110101001 asy 
					0x800849A9,  // 10000100100110101001 asy 
					0x80086B21   // 10000110101100100001 asy 
				},
				null,
				{   // 22-membered rings
					0x00084909,  // 0010000100100100001001 sym
					0x00021843,  // 0000100001100001000011 sym 
					0x00206121,  // 1000000110000100100001 sym
					0x00081903,  // 0010000001100100000011 sym
					0x0021AC35,  // 1000011010110000110101 sym 
					0x802A4D49,  // 1010100100110101001001 asy 
					0x00035849,  // 0000110101100001001001 sym 
					0x002B5909,  // 1010110101100100001001 sym 
					0x00021953,  // 0000100001100101010011 sym 
					0x80095909,  // 0010010101100100001001 asy
					0x80035959,  // 0000110101100101011001 asy
					0x00095D49,  // 0010010101110101001001 sym
					0x80206561,  // 1000000110010101100001 asy 
					0x800D1909,  // 0011010001100100001001 asy 
					0x000A9953,  // 0010101001100101010011 sym 
					0x00257535,  // 1001010111010100110101 sym 
					0x80207461,  // 1000000111010001100001 asy 
					0x80021D13,  // 0000100001110100010011 asy
					0x800876C9,  // 0010000111011011001001 asy 
					0x80086BA3,  // 0010000110101110100011 asy 
					0x802B5D49,  // 1010110101110101001001 asy 
					0x80081D43,  // 0010000001110101000011 asy 
					0x800D192B,  // 0011010001100100101011 asy 
					0x800D1D49,  // 0011010001110101001001 asy
					0x002B5D6B,  // 1010110101110101101011 sym 
					0x001066D9,  // 1000000110011011011001 sym 
					0x800D19A3,  // 0011010001100110100011 asy
					0x002AB953,  // 1010101011100101010011 sym
					0x802A1D43,  // 1010100001110101000011 asy 
					0x00021D57,  // 0000100001110101010111 sym 
					0x000D1C59,  // 0011010001110001011001 sym 
					0x8021DB35,  // 1000011101101100110101 asy
					0x80229903,  // 1000101001100100000011 asy 
					0x800D1D6B,  // 0011010001110101101011 asy
					0x802A76C9,  // 1010100111011011001001 asy 
					0x800876EB,  // 0010000111011011101011 asy
					0x80369909,  // 1101101001100100001001 asy 
					0x80347535,  // 1101000111010100110101 asy
					0x800A9917,  // 0010101001100100010111 asy 
					0x0022EBA3,  // 1000101110101110100011 sym 
					0x00084E97,  // 0010000100111010010111 sym
					0x00201C03,  // 1000000001110000000011 sym 
					0x8008B917,  // 0010001011100100010111 asy 
					0x802DD753,  // 1011011101011101010011 asy
					0x00377249,  // 1101110111001001001001 sym 
					0x80095CB7,  // 0010010101110010110111 asy 
					0x80081C17   // 0010000001110000010111 asy
				},
				null,
				{   // 24-membered rings
					0x00818181,  // 100000011000000110000001 sym
					0x002126D9,  // 001000010010011011011001 sym
					0x00204C03,  // 001000000100110000000011 sym
					0x00000000,  // 
					0x00000000,  // 
					0x00000000,  // 
					0x00000000,  // 
					0x00000000,  // 
					0x00000000,  // 
					0x00000000,  // 
					0x00000000,  // 
					0x00000000,  // 
					0x00000000,  // 
					0x00000000,  // 
					0x00000000,  // 
					0x00000000,  // 
					0x00000000,  // 
					0x00000000,  // 

					0x0086BB75   // 100001101011101101110101 sym
				}
			};

		int ringIndex = f.size()-10;
		if (f.size() >= 10 && f.size() <= 24 && cBondZList[ringIndex] != null) {
			int maxBit = (1 << f.size());
			int bondEConstraint = 0;
			int bondZConstraint = 0;
			for (int i=0; i<f.size(); i++) {
				if (mMol.getBondOrder(ringBond[i]) == 2) {
					int bondParity = mMol.getBondParity(ringBond[i]);
					if (bondParity == Molecule.cBondParityEor1)
						bondEConstraint += maxBit;
					if (bondParity == Molecule.cBondParityZor2)
						bondZConstraint += maxBit;
					}
				bondEConstraint >>>= 1;
				bondZConstraint >>>= 1;
				}

			for (int zList=0; zList<cBondZList[ringIndex].length; zList++) {
				boolean isSymmetrical = ((0x80000000 & cBondZList[ringIndex][zList]) == 0);
				int bondZList = (0x7FFFFFFF & cBondZList[ringIndex][zList]);
				for (boolean inverted=false; !inverted; inverted = !inverted) {
					if (inverted) {
						if (isSymmetrical)
							break;

						int newBondZList = 0;
						for (int bit=1; bit!=maxBit; bit<<=1) {
							newBondZList <<= 1;
							if ((bondZList & bit) != 0)
								newBondZList |= 1;
							}
						bondZList = newBondZList;
						}
					for (int rotation=0; rotation<f.size(); rotation++) {
						if ((bondZList & bondEConstraint) == 0
						 && (~bondZList & bondZConstraint) == 0) {
							// constraints are satisfied with current E/Z sequence
		
							double bondAngle = 0.0;
							boolean wasRightTurn = true; // create a ring that closes with right turns
							for (int i=1; i<f.size(); i++) {
								f.mAtomX[i] = f.mAtomX[i-1] + Math.sin(bondAngle);
								f.mAtomY[i] = f.mAtomY[i-1] + Math.cos(bondAngle);
								if ((bondZList & 1) == 0) // is E-bond
									wasRightTurn = !wasRightTurn;
								bondAngle += wasRightTurn ? Math.PI/3.0 : -Math.PI/3.0;
								bondZList >>>= 1;
								}
							return;
							}
						if ((bondZList & 1) != 0)
							bondZList |= maxBit;
						bondZList >>>= 1;
						}
					}
				}
			}

			// if not successful so far
		createRegularRingFragment(f);
		}


	private InventorChain getSmallestRingFromBond(int bond) {
			// find smallest ring of given bond
		int atom1 = mMol.getBondAtom(0, bond);
		int atom2 = mMol.getBondAtom(1, bond);
		int graphAtom[] = new int[mAtoms];
		int graphBond[] = new int[mAtoms];
		int graphLevel[] = new int[mAtoms];
		int graphParent[] = new int[mAtoms];
		graphAtom[0] = atom1;
		graphAtom[1] = atom2;
		graphBond[1] = bond;
		graphLevel[atom1] = 1;
		graphLevel[atom2] = 2;
		graphParent[0] = -1;
		graphParent[1] = 0;
		int current = 1;
		int highest = 1;
		while (current <= highest) {
//			if (graphLevel[graphAtom[current]] > RingCollection.MAX_LARGE_RING_SIZE)
//				return null;		// disabled ring size limit;  TLS 20130613
			for (int i=0; i<mConnAtoms[graphAtom[current]]; i++) {
				int candidate = mMol.getConnAtom(graphAtom[current], i);
				if ((current > 1) && candidate == atom1) {
					InventorChain theRing = new InventorChain(graphLevel[graphAtom[current]]);
					graphBond[0] = mMol.getConnBond(graphAtom[current], i);
					int index = current;
					for (int j=0; j<theRing.getChainLength(); j++) {
						theRing.mAtom[j] = graphAtom[index];
						theRing.mBond[j] = graphBond[index];
						index = graphParent[index];
						}
					return theRing;
					}
				if (graphLevel[candidate] == 0 && mMol.isRingAtom(candidate)) {
					graphAtom[++highest] = candidate;
					graphBond[highest] = mMol.getConnBond(graphAtom[current], i);
					graphLevel[candidate] = graphLevel[graphAtom[current]] + 1;
					graphParent[highest] = current;
					}
				}
			current++;
			}
		return null;
		}


	private int getSmallestRingSize(int atom1, int atom2, int atom3) {
			// return size of smallest ring where atom1, atom2 and atom3 occurr in this order
		int graphAtom[] = new int[mAtoms];
		int graphLevel[] = new int[mAtoms];
		graphAtom[0] = atom2;
		graphAtom[1] = atom1;
		graphLevel[atom2] = 1;
		graphLevel[atom1] = 2;
		int current = 1;
		int highest = 1;
		while (current <= highest) {
//			if (graphLevel[graphAtom[current]] > RingCollection.MAX_LARGE_RING_SIZE)
//				return 0;		// disabled ring size limit;  TLS 20130613
			for (int i=0; i<mConnAtoms[graphAtom[current]]; i++) {
				int candidate = mMol.getConnAtom(graphAtom[current], i);
				if (candidate == atom3)
					return 1 + graphLevel[graphAtom[current]];

				if (graphLevel[candidate] == 0 && mMol.isRingAtom(candidate)) {
					graphAtom[++highest] = candidate;
					graphLevel[candidate] = graphLevel[graphAtom[current]] + 1;
					}
				}
			current++;
			}
		return 0;
		}


	private void joinOverlappingFragments() {
		while (true) {
			int maxJoinPriority = 0;
			int maxCommonAtoms = 0;
			InventorFragment maxFragment1 = null;
			InventorFragment maxFragment2 = null;
			for (int i=1; i<mFragmentList.size(); i++) {
				InventorFragment f1 = mFragmentList.get(i);
				for (int j=0; j<i; j++) {
					InventorFragment f2 = mFragmentList.get(j);

					int commonAtom = 0;
					int commonAtoms = 0;
					int maxF1Priority = 0;
					int maxF2Priority = 0;
					for (int k=0; k<f1.size(); k++) {
						for (int l=0; l<f2.size(); l++) {
							if (f1.mAtom[k] == f2.mAtom[l]) {
								commonAtoms++;
								commonAtom = f1.mAtom[k];
								if (maxF1Priority < f1.mPriority[k])
									maxF1Priority = f1.mPriority[k];
								if (maxF2Priority < f2.mPriority[l])
									maxF2Priority = f2.mPriority[l];
								}
							}
						}

					if (commonAtoms > 0) {
						int handlePreferred = (commonAtoms == 1
											&& getConnAtoms(f1, commonAtom) == 1
											&& getConnAtoms(f2, commonAtom) == 1) ? 0 : 1;

						int joinPriority;
						if (maxF1Priority > maxF2Priority)
							joinPriority = (handlePreferred << 24)
										 + (maxF1Priority << 16)
										 + (maxF2Priority << 8)
										 + commonAtoms;
						else
							joinPriority = (handlePreferred << 24)
										 + (maxF2Priority << 16)
										 + (maxF1Priority << 8)
										 + commonAtoms;

						if (maxJoinPriority < joinPriority) {
							maxJoinPriority = joinPriority;
							maxCommonAtoms = commonAtoms;

							// retain coordinates of fragment with highest priority atom
							maxF1Priority = 0;
							maxF2Priority = 0;
							for (int k=0; k<f1.size(); k++)
								if (maxF1Priority < f1.mPriority[k])
									maxF1Priority = f1.mPriority[k];
							for (int k=0; k<f2.size(); k++)
								if (maxF2Priority < f2.mPriority[k])
									maxF2Priority = f2.mPriority[k];

							if (maxF1Priority > maxF2Priority) {
								maxFragment1 = f1;
								maxFragment2 = f2;
								}
							else {
								maxFragment1 = f2;
								maxFragment2 = f1;
								}
							}
						}
					}
				}

			if (maxJoinPriority == 0)
				break;

			if (maxCommonAtoms == maxFragment1.size())
				mFragmentList.remove(maxFragment1);
			else if (maxCommonAtoms == maxFragment2.size())
				mFragmentList.remove(maxFragment2);
			else
				joinFragments(maxFragment1, maxFragment2, maxCommonAtoms);
			}
		}


	private void joinFragments(InventorFragment f1, InventorFragment f2, int commonAtoms) {
		int[] commonAtom = new int[commonAtoms];
		int count = 0;
		for (int i=0; i<f1.mAtom.length; i++)
			for (int j=0; j<f2.mAtom.length; j++)
				if (f1.mAtom[i] == f2.mAtom[j])
					commonAtom[count++] = f1.mAtom[i];

		if (commonAtoms == 1)
			mFragmentList.add(getFusedFragment(f1, f2, commonAtom[0]));
		else
			mFragmentList.add(getFusedFragment(f1, f2, commonAtom, commonAtoms));

		mFragmentList.remove(f1);
		mFragmentList.remove(f2);
		}


	private InventorFragment getFusedFragment(InventorFragment f1, InventorFragment f2, int commonAtom) {
		int index1 = f1.getIndex(commonAtom);
		int index2 = f2.getIndex(commonAtom);

		f2.translate(f1.mAtomX[index1]-f2.mAtomX[index2],
					 f1.mAtomY[index1]-f2.mAtomY[index2]);

		double angle1 = suggestNewBondAngle(f1, commonAtom);
		double angle2 = suggestNewBondAngle(f2, commonAtom);

		double angleInc = 0.0;
		if (getConnAtoms(f1, commonAtom) == 1
		 && getConnAtoms(f2, commonAtom) == 1)
			angleInc = Math.PI / 3;

		f2.rotate(f2.mAtomX[index2], f2.mAtomY[index2], angle1 - angle2 + angleInc + Math.PI);

		return getMergedFragment(f1, f2, 1);
		}


	private InventorFragment getFusedFragment(InventorFragment f1, InventorFragment f2,
											  int[] commonAtom, int commonAtoms) {
		int[] index1 = new int[commonAtoms];
		int[] index2 = new int[commonAtoms];
		for (int i=0; i<commonAtoms; i++) {
			index1[i] = f1.getIndex(commonAtom[i]);
			index2[i] = f2.getIndex(commonAtom[i]);
			}

		double meanX1 = 0.0;
		double meanY1 = 0.0;
		double meanX2 = 0.0;
		double meanY2 = 0.0;

		for (int i=0; i<commonAtoms; i++) {
			meanX1 += f1.mAtomX[index1[i]];
			meanY1 += f1.mAtomY[index1[i]];
			meanX2 += f2.mAtomX[index2[i]];
			meanY2 += f2.mAtomY[index2[i]];
			}
		meanX1 /= commonAtoms;
		meanY1 /= commonAtoms;
		meanX2 /= commonAtoms;
		meanY2 /= commonAtoms;
		f2.translate(meanX1 - meanX2, meanY1 - meanY2);

		InventorAngle[] f1Angle = new InventorAngle[commonAtoms];
		InventorAngle[] f2Angle = new InventorAngle[commonAtoms];
		InventorAngle[] angleDif = new InventorAngle[commonAtoms];
		InventorAngle[] angleDifFlip = new InventorAngle[commonAtoms];
		for (int i=0; i<commonAtoms; i++) {
			f1Angle[i] = new InventorAngle(meanX1, meanY1, f1.mAtomX[index1[i]], f1.mAtomY[index1[i]]);
			f2Angle[i] = new InventorAngle(meanX1, meanY1, f2.mAtomX[index2[i]], f2.mAtomY[index2[i]]);
			angleDif[i] = new InventorAngle(f1Angle[i].mAngle - f2Angle[i].mAngle,
											f1Angle[i].mLength * f2Angle[i].mLength);
			angleDifFlip[i] = new InventorAngle(f1Angle[i].mAngle + f2Angle[i].mAngle,
												f1Angle[i].mLength * f2Angle[i].mLength);
			}
		InventorAngle meanAngleDif = getMeanAngle(angleDif, commonAtoms);
		InventorAngle meanAngleDifFlip = getMeanAngle(angleDifFlip, commonAtoms);

		int neighbourCountF1 = 0;
		int neighbourCountF2 = 0;
		for (int i=0; i<commonAtoms; i++) {
			for (int j=0; j<mConnAtoms[commonAtom[i]]; j++) {
				int connAtom = mMol.getConnAtom(commonAtom[i], j);

				if (f1.isMember(connAtom) && !f2.isMember(connAtom))
					neighbourCountF1++;

				if (!f1.isMember(connAtom) && f2.isMember(connAtom))
					neighbourCountF2++;
				}
			}

		InventorAngle[] f1NeighbourAngle = new InventorAngle[neighbourCountF1];
		InventorAngle[] f2NeighbourAngle = new InventorAngle[neighbourCountF2];
		InventorAngle[] f2NeighbourAngleFlip = new InventorAngle[neighbourCountF2];
		neighbourCountF1 = 0;
		neighbourCountF2 = 0;
		for (int i=0; i<commonAtoms; i++) {
			for (int j=0; j<mConnAtoms[commonAtom[i]]; j++) {
				int connAtom = mMol.getConnAtom(commonAtom[i], j);

				if (f1.isMember(connAtom) && !f2.isMember(connAtom)) {
					int connIndex = f1.getIndex(connAtom);
					f1NeighbourAngle[neighbourCountF1] = new InventorAngle(f1.mAtomX[index1[i]],
																		   f1.mAtomY[index1[i]],
																		   f1.mAtomX[connIndex],
																		   f1.mAtomY[connIndex]);
					neighbourCountF1++;
					}

				if (!f1.isMember(connAtom) && f2.isMember(connAtom)) {
					int connIndex = f2.getIndex(connAtom);
					InventorAngle neighbourAngle = new InventorAngle(f2.mAtomX[index2[i]],
																	 f2.mAtomY[index2[i]],
																	 f2.mAtomX[connIndex],
																	 f2.mAtomY[connIndex]);
					f2NeighbourAngle[neighbourCountF2] = new InventorAngle(meanAngleDif.mAngle
																		   + neighbourAngle.mAngle,
																		   neighbourAngle.mLength);
					f2NeighbourAngleFlip[neighbourCountF2] = new InventorAngle(meanAngleDifFlip.mAngle
																			   - neighbourAngle.mAngle,
																			   neighbourAngle.mLength);
					neighbourCountF2++;
					}
				}
			}

		InventorAngle meanNeighbourAngleF1 = getMeanAngle(f1NeighbourAngle, neighbourCountF1);
		InventorAngle meanNeighbourAngleF2 = getMeanAngle(f2NeighbourAngle, neighbourCountF2);
		InventorAngle meanNeighbourAngleF2Flip = getMeanAngle(f2NeighbourAngleFlip, neighbourCountF2);

		if (Math.abs(getAngleDif(meanNeighbourAngleF1.mAngle, meanNeighbourAngleF2.mAngle))
		  > Math.abs(getAngleDif(meanNeighbourAngleF1.mAngle, meanNeighbourAngleF2Flip.mAngle))) {
			f2.rotate(meanX1, meanY1, meanAngleDif.mAngle);
			}
		else {
			f2.flip(meanX1, meanY1, 0.0);
			f2.rotate(meanX1, meanY1, meanAngleDifFlip.mAngle);
			}

		return getMergedFragment(f1, f2, commonAtoms);
		}


	private InventorFragment getMergedFragment(InventorFragment f1, InventorFragment f2, int commonAtoms) {
			// merges all atoms of two fragments into a new one retaining original coordinates
		InventorFragment f = new InventorFragment(mMol, f1.mAtom.length + f2.mAtom.length - commonAtoms);
		int count = 0;
		for (int i=0; i<f1.mAtom.length; i++) {
			f.mAtom[count] = f1.mAtom[i];
			f.mPriority[count] = f1.mPriority[i];
			f.mAtomX[count] = f1.mAtomX[i];
			f.mAtomY[count++] = f1.mAtomY[i];
			}
		for (int i=0; i<f2.mAtom.length; i++) {
			int index = f1.getIndex(f2.mAtom[i]);
			if (index == -1) {
				f.mAtom[count] = f2.mAtom[i];
				f.mPriority[count] = f2.mPriority[i];
				f.mAtomX[count] = f2.mAtomX[i];
				f.mAtomY[count++] = f2.mAtomY[i];
				}
			else {
				if (f.mPriority[index] < f2.mPriority[i])
					f.mPriority[index] = f2.mPriority[i];
				}
			}
		return f;
		}


	private InventorChain getLongestUnhandledChain(int atom) {
		int graphAtom[] = new int[mAtoms];
		int graphBond[] = new int[mAtoms];
		int graphLevel[] = new int[mAtoms];
		int graphParent[] = new int[mAtoms];
		graphAtom[0] = atom;
		graphLevel[atom] = 1;
		graphParent[0] = -1;
		int current = 0;
		int highest = 0;
		while (current <= highest) {
			if (current == 0 || !mAtomHandled[graphAtom[current]]) {
				for (int i=0; i<mConnAtoms[graphAtom[current]]; i++) {
					int candidate = mMol.getConnAtom(graphAtom[current], i);
					int theBond = mMol.getConnBond(graphAtom[current], i);
					if (graphLevel[candidate] == 0 && !mBondHandled[theBond]) {
						graphAtom[++highest] = candidate;
						graphBond[highest] = theBond;
						graphLevel[candidate] = graphLevel[graphAtom[current]] + 1;
						graphParent[highest] = current;
						}
					}
				}
			if (current == highest) {
				InventorChain theChain = new InventorChain(graphLevel[graphAtom[current]]);
				int index = current;
				for (int j=0; j<theChain.getChainLength(); j++) {
					theChain.mAtom[j] = graphAtom[index];
					theChain.mBond[j] = graphBond[index];
					index = graphParent[index];
					}
				return theChain;
				}
			current++;
			}
		return null;
		}


	private double suggestNewBondAngle(InventorFragment f, int atom) {
		double[] connAngle = new double[mConnAtoms[atom]+1];
		int[] connAtom = new int[mConnAtoms[atom]+1];
		int[] connBond = new int[mConnAtoms[atom]+1];
		int rootIndex = f.getIndex(atom);
		int connAngles = 0;
		for (int i=0; i<mConnAtoms[atom]; i++) {
			connAtom[connAngles] = mMol.getConnAtom(atom, i);
			connBond[connAngles] = mMol.getConnBond(atom, i);
			int index = f.getIndex(connAtom[connAngles]);
			if (index != -1)
				connAngle[connAngles++] = InventorAngle.getAngle(f.mAtomX[rootIndex],
																 f.mAtomY[rootIndex],
																 f.mAtomX[index],
																 f.mAtomY[index]);
			}

		if (connAngles == 1)
			return connAngle[0] + Math.PI;

		for (int i=connAngles-1; i>0; i--) {	// bubble sort
			for (int j=0; j<i; j++) {
				if (connAngle[j] > connAngle[j+1]) {
					double tempAngle = connAngle[j];
					connAngle[j] = connAngle[j+1];
					connAngle[j+1] = tempAngle;
					int tempAtom = connAtom[j];
					connAtom[j] = connAtom[j+1];
					connAtom[j+1] = tempAtom;
					int tempBond = connBond[j];
					connBond[j] = connBond[j+1];
					connBond[j+1] = tempBond;
					}
				}
			}

		connAngle[connAngles] = connAngle[0] + 2 * Math.PI;
		connAtom[connAngles] = connAtom[0];
		connBond[connAngles] = connBond[0];

		double maxAngleDif = -100.0;
		int maxIndex = 0;
		for (int i=0; i<connAngles; i++) {
			double angleDif = connAngle[i+1] - connAngle[i];
			if (connAngles > 2
			 && mMol.isRingBond(connBond[i])
			 && mMol.isRingBond(connBond[i+1])) {
				int ringSize = getSmallestRingSize(connAtom[i], atom, connAtom[i+1]);
				if (ringSize != 0)
					angleDif -= 100.0 - ringSize;
				}

			if (maxAngleDif < angleDif) {
				maxAngleDif = angleDif;
				maxIndex = i;
				}
			}

		return (connAngle[maxIndex] + connAngle[maxIndex+1]) / 2;
		}


	private double getAngleDif(double angle1, double angle2) {
		double angleDif = angle1 - angle2;
		while (angleDif < -Math.PI)
			angleDif += 2 * Math.PI;
		while (angleDif > Math.PI)
			angleDif -= 2 * Math.PI;
		return angleDif;
		}


	protected static InventorAngle getMeanAngle(InventorAngle[] angle, int noOfAngles) {
		// adds noOfAngles vectors of length=1 with angles angle[i]
		// and returns angle of sum-vector
		// length of sum-vector is criteria for deviation
		double sinSum = 0;
		double cosSum = 0;
		for (int i=0; i<noOfAngles; i++) {
			sinSum += angle[i].mLength * Math.sin(angle[i].mAngle);
			cosSum += angle[i].mLength * Math.cos(angle[i].mAngle);
			}

		double meanAngle;
		if (cosSum == 0)
			meanAngle = (sinSum > 0) ? Math.PI/2 : -Math.PI/2;
		else {
			meanAngle = Math.atan(sinSum/cosSum);
			if (cosSum < 0)
				meanAngle += Math.PI;
			}

		double length = Math.sqrt(sinSum * sinSum + cosSum * cosSum) / noOfAngles;

		return new InventorAngle(meanAngle, length);
		}


	private int getConnAtoms(InventorFragment f, int atom) {
		int connAtoms = 0;
		for (int i=0; i<mConnAtoms[atom]; i++) {
			if (f.isMember(mMol.getConnAtom(atom, i)))
				connAtoms++;
			}
		return connAtoms;
		}


	private void locateFragmentBonds() {
		for (int fragmentNo=0; fragmentNo<mFragmentList.size(); fragmentNo++) {
			InventorFragment f = mFragmentList.get(fragmentNo);
			f.locateBonds();
			}
		}


	private void correctChainEZParities() {
		for (int fragmentNo=0; fragmentNo<mFragmentList.size(); fragmentNo++) {
			InventorFragment f = mFragmentList.get(fragmentNo);
			for (int i=0; i<f.mBond.length; i++) {
				int bond = f.mBond[i];

				if (mMol.getBondOrder(bond) == 2) {
					if (!mMol.isSmallRingBond(bond)
					 && mMol.getBondParity(bond) == Molecule.cBondParityNone)
						mMol.setBondParityUnknownOrNone(bond);
	
					if (!mMol.isRingBond(bond)
					 && (mMol.getConnAtoms(mMol.getBondAtom(0, bond)) > 1)
					 && (mMol.getConnAtoms(mMol.getBondAtom(1, bond)) > 1)
					 && (mMol.getBondParity(bond) == Molecule.cBondParityEor1
					  || mMol.getBondParity(bond) == Molecule.cBondParityZor2)) {
						int[] minConnAtom = new int[2];
						int[] bondAtom = new int[2];
						for (int j=0; j<2; j++) {
							minConnAtom[j] = mMol.getMaxAtoms();
							bondAtom[j] = mMol.getBondAtom(j, bond);
							for (int k=0; k<mConnAtoms[bondAtom[j]]; k++) {
								int connAtom = mMol.getConnAtom(bondAtom[j], k);
								if (connAtom != mMol.getBondAtom(1-j, bond)
								 && minConnAtom[j] > connAtom)
									minConnAtom[j] = connAtom;
								}
							}
	
						double dbAngle = InventorAngle.getAngle(f.mAtomX[f.mAtomIndex[bondAtom[0]]],
																f.mAtomY[f.mAtomIndex[bondAtom[0]]],
																f.mAtomX[f.mAtomIndex[bondAtom[1]]],
																f.mAtomY[f.mAtomIndex[bondAtom[1]]]);
						double angle1  = InventorAngle.getAngle(f.mAtomX[f.mAtomIndex[minConnAtom[0]]],
																f.mAtomY[f.mAtomIndex[minConnAtom[0]]],
																f.mAtomX[f.mAtomIndex[bondAtom[0]]],
																f.mAtomY[f.mAtomIndex[bondAtom[0]]]);
						double angle2  = InventorAngle.getAngle(f.mAtomX[f.mAtomIndex[bondAtom[1]]],
																f.mAtomY[f.mAtomIndex[bondAtom[1]]],
																f.mAtomX[f.mAtomIndex[minConnAtom[1]]],
																f.mAtomY[f.mAtomIndex[minConnAtom[1]]]);
	
						if (((getAngleDif(dbAngle, angle1) < 0)
						   ^ (getAngleDif(dbAngle, angle2) < 0))
						  ^ (mMol.getBondParity(bond) == Molecule.cBondParityZor2)) {
							f.flipOneSide(bond);
							}
						}
					}
				}
			}
		}


	private void optimizeFragments() {
		int[] atomSymRank = calculateAtomSymmetries();

		byte[] bondFlipPriority = new byte[mBonds];
		locateFlipBonds(bondFlipPriority, atomSymRank);
		for (int bond=0; bond<mBonds; bond++)
			if (bondFlipPriority[bond] == FLIP_POSSIBLE
			 && (mMol.isRingAtom(mMol.getBondAtom(0, bond))
			  || mMol.isRingAtom(mMol.getBondAtom(1, bond))))
				bondFlipPriority[bond] = FLIP_PREFERRED;

		for (int fragmentNo=0; fragmentNo<mFragmentList.size(); fragmentNo++) {
			InventorFragment f = mFragmentList.get(fragmentNo);
			ArrayList<int[]> collisionList = f.getCollisionList();
			double minCollisionPanalty = f.getCollisionPanalty();
			InventorFragment minCollisionFragment = new InventorFragment(f);

			int lastBond = -1;
			for (int flip=0; flip<TOTAL_FLIPS && collisionList.size()!=0; flip++) {
				int collisionNo = mRandom.nextInt(collisionList.size());
				int[] collidingAtom = collisionList.get(collisionNo);
				int[] bondSequence = getShortestConnection(collidingAtom[0], collidingAtom[1]);
				int[] availableBond = new int[bondSequence.length];
				int availableBonds = 0;

				if (flip < PREFERRED_FLIPS) {
					for (int i=1; i<bondSequence.length-1; i++)
						if (bondFlipPriority[bondSequence[i]] == FLIP_PREFERRED)
							availableBond[availableBonds++] = bondSequence[i];
					}
				else if (flip < PREFERRED_FLIPS + POSSIBLE_FLIPS) {
					for (int i=1; i<bondSequence.length-1; i++)
						if (bondFlipPriority[bondSequence[i]] >= FLIP_POSSIBLE)
							availableBond[availableBonds++] = bondSequence[i];
					}
				else {
					for (int i=1; i<bondSequence.length-1; i++)
						if (bondFlipPriority[bondSequence[i]] >= FLIP_AS_LAST_RESORT)
							availableBond[availableBonds++] = bondSequence[i];
					}

				if (availableBonds != 0) {
					int theBond = availableBond[0];
					if (availableBonds > 1) {
						do {	// don't rotate 2 times around same bond
							theBond = availableBond[mRandom.nextInt(availableBonds)];
							} while (theBond == lastBond);
						}

					if (theBond != lastBond) {
						lastBond = theBond;
		
						f.flipOneSide(theBond);

							// getCollisionList() is necessary to update collision panalty
						collisionList = f.getCollisionList();
						if (minCollisionPanalty > f.getCollisionPanalty()) {
							minCollisionPanalty = f.getCollisionPanalty();
							minCollisionFragment = new InventorFragment(f);
							}
						}
					}
				}

			mFragmentList.set(fragmentNo, minCollisionFragment);
				// finished optimization by rotating around single bonds

				// starting optimization by moving individual atoms
/*
double avbl = mMol.getAverageBondLength();
for (int i=0; i<f.mAtom.length; i++) {
f.mAtomX[i] = mMol.getAtomX(f.mAtom[i]) / avbl;
f.mAtomY[i] = mMol.getAtomY(f.mAtom[i]) / avbl;
}*/
			f = minCollisionFragment;
			int currentRank = 1;
			int nextAvailableRank;
			do {
				nextAvailableRank = 9999;
				for (int i=0; i<f.size(); i++) {
					int theRank = atomSymRank[f.mAtom[i]];
					if (theRank == currentRank)
						f.optimizeAtomCoordinates(i);
					else if (theRank > currentRank && theRank < nextAvailableRank)
						nextAvailableRank = theRank;
					}
				currentRank = nextAvailableRank;
				} while (nextAvailableRank != 9999);
			}
		}


	private int[] getShortestConnection(int atom1, int atom2) {
		int graphAtom[] = new int[mAtoms];
		int graphBond[] = new int[mAtoms];
		int graphLevel[] = new int[mAtoms];
		int graphParent[] = new int[mAtoms];
		graphAtom[0] = atom2;
		graphLevel[atom2] = 1;
		graphParent[0] = -1;
		int current = 0;
		int highest = 0;
		while (current <= highest) {
			for (int i=0; i<mConnAtoms[graphAtom[current]]; i++) {
				int candidate = mMol.getConnAtom(graphAtom[current], i);
				int theBond = mMol.getConnBond(graphAtom[current], i);

				if (candidate == atom1) {
					int chainLength = graphLevel[graphAtom[current]];
					int[] bondSequence = new int[chainLength];
					bondSequence[0] = theBond;
					for (int j=1; j<chainLength; j++) {
						bondSequence[j] = graphBond[current];
						current = graphParent[current];
						}

					return bondSequence;
					}

				if (graphLevel[candidate] == 0) {
					graphAtom[++highest] = candidate;
					graphBond[highest] = theBond;
					graphLevel[candidate] = graphLevel[graphAtom[current]] + 1;
					graphParent[highest] = current;
					}
				}

			if (current == highest)
				return null;

			current++;
			}
		return null;
		}


	private void locateFlipBonds(byte[] bondFlipPriority, int[] atomSymRank) {
		for (int bond=0; bond<mBonds; bond++) {
			int atom1 = mMol.getBondAtom(0, bond);
			int atom2 = mMol.getBondAtom(1, bond);

			if (mMol.isRingBond(bond)
			 || mMol.getBondOrder(bond) != 1
			 || mConnAtoms[atom1] == 1
			 || mConnAtoms[atom2] == 1)
				continue;

			if ((mMode & MODE_KEEP_MARKED_ATOM_COORDS) != 0
			 && mMol.isMarkedAtom(atom1)
			 && mMol.isMarkedAtom(atom2))
			 	continue;

			boolean oneBondEndIsSymmetric = false;
			for (int i=0; i<2; i++) {
				int bondAtom = mMol.getBondAtom(i, bond);
				if (mConnAtoms[bondAtom] > 2) {
					boolean symmetricEndFound = true;
					int connSymRank = -1;
					for (int j=0; j<mConnAtoms[bondAtom]; j++) {
						int connAtom = mMol.getConnAtom(bondAtom, j);
						if (connAtom != mMol.getBondAtom(1-i, bond)) {
							if (connSymRank == -1)
								connSymRank = atomSymRank[connAtom];
							else if (connSymRank != atomSymRank[connAtom])
								symmetricEndFound = false;
							}
						}
					if (symmetricEndFound) {
						oneBondEndIsSymmetric = true;
						break;
						}
					}
				}
			if (!oneBondEndIsSymmetric) {
				if ((mMode & MODE_PREFER_MARKED_ATOM_COORDS) != 0
				 && mMol.isMarkedAtom(atom1)
				 && mMol.isMarkedAtom(atom2))
					bondFlipPriority[bond] = FLIP_AS_LAST_RESORT;
				else
					bondFlipPriority[bond] = FLIP_POSSIBLE;
				}
			}
		}


	private int[] calculateAtomSymmetries() {
		CanonizerBaseValue[] baseValue = new CanonizerBaseValue[mAtoms];
		for (int atom=0; atom<mAtoms; atom++) {
			baseValue[atom] = new CanonizerBaseValue(2);
			baseValue[atom].init(atom);
			}

		int[] symRank = new int[mAtoms];

			// For calculating atom symmetries all atoms are considered the same.
			// Only the different connectivity makes an atom different from another.
			// However, double bonds of different parities must be taken into account.
		for (int bond=0; bond<mMol.getBonds(); bond++) {
			int bondParity = mMol.getBondParity(bond);
			if (bondParity == Molecule.cBondParityEor1
			 || bondParity == Molecule.cBondParityZor2) {
				baseValue[mMol.getBondAtom(0, bond)].add(bondParity);
				baseValue[mMol.getBondAtom(1, bond)].add(bondParity);
				}
			}

		int oldNoOfRanks;
		int newNoOfRanks = consolidateRanks(baseValue, symRank);
		do {
			oldNoOfRanks = newNoOfRanks;
			calcNextBaseValues(baseValue, symRank);
			newNoOfRanks = consolidateRanks(baseValue, symRank);
			} while (oldNoOfRanks != newNoOfRanks);

		return symRank;
		}


	private void calcNextBaseValues(CanonizerBaseValue[] baseValue, int[] symRank) {
		int	connRank[] = new int[ExtendedMolecule.cMaxConnAtoms];
		for (int atom=0; atom<mAtoms; atom++) {
								// generate sorted list of ranks of neighbours
			for (int i=0; i<mConnAtoms[atom]; i++) {
				int rank = symRank[mMol.getConnAtom(atom,i)];
				int j;
				for (j=0; j<i; j++)
					if (rank < connRank[j])
						break;
				for (int k=i; k>j; k--)
					connRank[k] = connRank[k-1];
				connRank[j] = rank;
				}

			int neighbours = Math.min(6, mConnAtoms[atom]);
			baseValue[atom].init(atom);
			baseValue[atom].add(Canonizer.ATOM_BITS, symRank[atom]);
			baseValue[atom].add((6 - neighbours)*(Canonizer.ATOM_BITS + 1), 0);
			for (int i=0; i<neighbours; i++)
				baseValue[atom].add(Canonizer.ATOM_BITS + 1, connRank[i]);
			}
		}


	private int consolidateRanks(CanonizerBaseValue[] baseValue, int[] symRank) {
		int rank = 0;
		Arrays.sort(baseValue);
		for (int i=0; i<baseValue.length; i++) {
			if (i == 0 || baseValue[i].compareTo(baseValue[i-1]) != 0)
				rank++;
			symRank[baseValue[i].getAtom()] = rank;
			}

		return rank;
		}


	private void arrangeAllFragments() {
		while (mFragmentList.size() > 1) {
			double[] largeFragmentSize = new double[2];
			InventorFragment[] largeFragment = new InventorFragment[2];

				// find largest two Fragments (in terms of display size)
			InventorFragment f0 = mFragmentList.get(0);
			InventorFragment f1 = mFragmentList.get(1);
			double s0 = f0.getWidth() + f0.getHeight();
			double s1 = f1.getWidth() + f1.getHeight();
			if (s0 > s1) {
				largeFragment[0] = f0;
				largeFragmentSize[0] = s0;
				largeFragment[1] = f1;
				largeFragmentSize[1] = s1;
				}
			else {
				largeFragment[0] = f1;
				largeFragmentSize[0] = s1;
				largeFragment[1] = f0;
				largeFragmentSize[1] = s0;
				}

			for (int i=2; i<mFragmentList.size(); i++) {
				InventorFragment fn = mFragmentList.get(i);
				double sn = fn.getWidth() + fn.getHeight();
				if (largeFragmentSize[0] < sn) {
					largeFragment[1] = largeFragment[0];
					largeFragment[0] = fn;
					largeFragmentSize[1] = largeFragmentSize[0];
					largeFragmentSize[0] = sn;
					}
				else if (largeFragmentSize[1] < sn) {
					largeFragment[1] = fn;
					largeFragmentSize[1] = sn;
					}
				}

			largeFragment[0].arrangeWith(largeFragment[1]);
			mFragmentList.add(getMergedFragment(largeFragment[0], largeFragment[1], 0));
			mFragmentList.remove(largeFragment[0]);
			mFragmentList.remove(largeFragment[1]);
			}
		}


	class InventorFragment {
		private static final double cCollisionLimitBondRotation = 0.8;
		private static final double cCollisionLimitAtomMovement = 0.5;

		private StereoMolecule mMol;
		protected int[] mAtom;
		protected int[] mBond;
		protected int[] mAtomIndex;
		protected int[] mPriority;
		protected double[] mAtomX;
		protected double[] mAtomY;
		private boolean	mMinMaxAvail;
		private double mMinX;
		private double mMinY;
		private double mMaxX;
		private double mMaxY;
		private double mCollisionPanalty;
		private int[][] mFlipList;

		protected InventorFragment(StereoMolecule mol, int atoms) {
			mMol = mol;
			mAtom = new int[atoms];
			mPriority = new int[atoms];
			mAtomX = new double[atoms];
			mAtomY = new double[atoms];
			}

		protected InventorFragment(InventorFragment f) {
			mMol = f.mMol;
			mAtom = new int[f.size()];
			mPriority = new int[f.size()];
			mAtomX = new double[f.size()];
			mAtomY = new double[f.size()];
			for (int i=0; i<f.size(); i++) {
				mAtom[i]	 = f.mAtom[i];
				mPriority[i] = f.mPriority[i];
				mAtomX[i]	 = f.mAtomX[i];
				mAtomY[i]	 = f.mAtomY[i];
				}
			if (f.mBond != null) {
				mBond = new int[f.mBond.length];
				for (int i=0; i<f.mBond.length; i++)
					mBond[i] = f.mBond[i];
				}
			if (f.mAtomIndex != null) {
				mAtomIndex = new int[f.mAtomIndex.length];
				for (int i=0; i<f.mAtomIndex.length; i++)
					mAtomIndex[i] = f.mAtomIndex[i];
				}
			}

		protected int size() {
			return mAtom.length;
			}

		protected double getWidth() {
			calculateMinMax();
			return mMaxX - mMinX + 1.0;	// add half a bond length on every side
			}

		protected double getHeight() {
			calculateMinMax();
			return mMaxY - mMinY + 1.0;	// add half a bond length on every side
			}

		protected boolean isMember(int atom) {
			for (int i=0; i<mAtom.length; i++)
				if (atom == mAtom[i])
					return true;

			return false;
			}

		protected int getIndex(int atom) {
			for (int i=0; i<mAtom.length; i++)
				if (atom == mAtom[i])
					return i;

			return -1;
			}

		protected void translate(double dx, double dy) {
			for (int i=0; i<mAtom.length; i++) {
				mAtomX[i] += dx;
				mAtomY[i] += dy;
				}
			}

		protected void rotate(double x, double y, double angleDif) {
			for (int i=0; i<mAtom.length; i++) {
				double distance = Math.sqrt((mAtomX[i] - x) * (mAtomX[i] - x)
										  + (mAtomY[i] - y) * (mAtomY[i] - y));
				double angle = InventorAngle.getAngle(x, y, mAtomX[i], mAtomY[i]) + angleDif;
				mAtomX[i] = x + distance * Math.sin(angle);
				mAtomY[i] = y + distance * Math.cos(angle);
				}
			}

		protected void flip(double x, double y, double mirrorAngle) {
			for (int i=0; i<mAtom.length; i++) {
				double distance = Math.sqrt((mAtomX[i] - x) * (mAtomX[i] - x)
										  + (mAtomY[i] - y) * (mAtomY[i] - y));
				double angle = 2 * mirrorAngle - InventorAngle.getAngle(x, y, mAtomX[i], mAtomY[i]);
				mAtomX[i] = x + distance * Math.sin(angle);
				mAtomY[i] = y + distance * Math.cos(angle);
				}
			}

		protected void flipOneSide(int bond) {
				// The fliplist contains for every bond atoms:
				// [0]->the bond atom that lies on the larger side of the bond
				// [1]->the bond atom on the smaller side of the bond
				// [2...n]->all other atoms on the smaller side of the bond.
				//		  These are the ones getting flipped on the mirror
				//		  line defined by the bond.
			if (mFlipList == null)
				mFlipList = new int[mBonds][];

			if (mFlipList[bond] == null) {
				int[] graphAtom = new int[mAtom.length];
				boolean[] isOnSide = new boolean[mAtoms];
				int atom1 = mMol.getBondAtom(0, bond);
				int atom2 = mMol.getBondAtom(1, bond);
				graphAtom[0] = atom1;
				isOnSide[atom1] = true;
				int current = 0;
				int highest = 0;
				while (current <= highest) {
					for (int i=0; i<mConnAtoms[graphAtom[current]]; i++) {
						int candidate = mMol.getConnAtom(graphAtom[current], i);
	
						if (!isOnSide[candidate] && candidate != atom2) {
							graphAtom[++highest] = candidate;
							isOnSide[candidate] = true;
							}
						}
					if (current == highest)
						break;
					current++;
					}

				// default is to flip the smaller side
				boolean flipOtherSide = (highest+1 > mAtom.length/2);

				// if we retain core atoms and the smaller side contains core atoms, then flip the larger side
				if ((mMode & MODE_CONSIDER_MARKED_ATOMS) != 0) {
					boolean coreOnSide = false;
					boolean coreOffSide = false;
					for (int i=0; i<mAtom.length; i++) {
						if (mMol.isMarkedAtom(mAtom[i])) {
							if (isOnSide[mAtom[i]])
								coreOnSide = true;
							else
								coreOffSide = true;
							}
						}
					if (coreOnSide != coreOffSide)
						flipOtherSide = coreOnSide;
					}

				int count = 2;
				mFlipList[bond] = new int[flipOtherSide ? mAtom.length-highest : highest+2];
				for (int i=0; i<mAtom.length; i++) {
					if (mAtom[i] == atom1)
						mFlipList[bond][flipOtherSide ? 0 : 1] = i;
					else if (mAtom[i] == atom2)
						mFlipList[bond][flipOtherSide ? 1 : 0] = i;
					else if (flipOtherSide ^ isOnSide[mAtom[i]])
						mFlipList[bond][count++] = i;
					}
				}

			double x = mAtomX[mFlipList[bond][0]];
			double y = mAtomY[mFlipList[bond][0]];
			double mirrorAngle = InventorAngle.getAngle(x, y, mAtomX[mFlipList[bond][1]],
															  mAtomY[mFlipList[bond][1]]);

			for (int i=2; i<mFlipList[bond].length; i++) {
				int index = mFlipList[bond][i];
				double distance = Math.sqrt((mAtomX[index] - x) * (mAtomX[index] - x)
										  + (mAtomY[index] - y) * (mAtomY[index] - y));
				double angle = 2 * mirrorAngle - InventorAngle.getAngle(x, y, mAtomX[index], mAtomY[index]);
				mAtomX[index] = x + distance * Math.sin(angle);
				mAtomY[index] = y + distance * Math.cos(angle);
				}
			}

		protected void arrangeWith(InventorFragment f) {
			double maxGain = 0.0;
			int maxCorner = 0;
			for (int corner=0; corner<4; corner++) {
				double gain = getCornerDistance(corner) + f.getCornerDistance((corner>=2) ? corner-2 : corner+2);
				if (maxGain < gain) {
					maxGain = gain;
					maxCorner = corner;
					}
				}

			double sumHeight = getHeight() + f.getHeight();
			double sumWidth = 0.75 * (getWidth() + f.getWidth());
			double maxHeight = Math.max(getHeight(), f.getHeight());
			double maxWidth = 0.75 * Math.max(getWidth(), f.getWidth());

			double bestCornerSize = Math.sqrt((sumHeight - maxGain) * (sumHeight - maxGain)
											+ (sumWidth - 0.75 * maxGain) * (sumWidth - 0.75 * maxGain));
			double toppedSize = Math.max(maxWidth, sumHeight);
			double besideSize = Math.max(maxHeight, sumWidth);

			if (bestCornerSize < toppedSize && bestCornerSize < besideSize) {
				switch(maxCorner) {
				case 0:
					f.translate(mMaxX - f.mMinX - maxGain + 1.0, mMinY - f.mMaxY + maxGain - 1.0);
					break;
				case 1:
					f.translate(mMaxX - f.mMinX - maxGain + 1.0, mMaxY - f.mMinY - maxGain + 1.0);
					break;
				case 2:
					f.translate(mMinX - f.mMaxX + maxGain - 1.0, mMaxY - f.mMinY - maxGain + 1.0);
					break;
				case 3:
					f.translate(mMinX - f.mMaxX + maxGain - 1.0, mMinY - f.mMaxY + maxGain - 1.0);
					break;
					}
				}
			else if (besideSize < toppedSize) {
				f.translate(mMaxX - f.mMinX + 1.0, (mMaxY + mMinY - f.mMaxY - f.mMinY) / 2);
				}
			else {
				f.translate((mMaxX + mMinX - f.mMaxX - f.mMinX) / 2, mMaxY - f.mMinY + 1.0);
				}
			}

		private void calculateMinMax() {
			if (mMinMaxAvail)
				return;

			mMinX = mAtomX[0];
			mMaxX = mAtomX[0];
			mMinY = mAtomY[0];
			mMaxY = mAtomY[0];
			for (int i=0; i<mAtom.length; i++) {
				double surplus = getAtomSurplus(i);

				if (mMinX > mAtomX[i] - surplus)
					mMinX = mAtomX[i] - surplus;
				if (mMaxX < mAtomX[i] + surplus)
					mMaxX = mAtomX[i] + surplus;
				if (mMinY > mAtomY[i] - surplus)
					mMinY = mAtomY[i] - surplus;
				if (mMaxY < mAtomY[i] + surplus)
					mMaxY = mAtomY[i] + surplus;
				}

			mMinMaxAvail = true;
			}

		private double getCornerDistance(int corner) {
			double minDistance = 9999.0;
			for (int atom=0; atom<mAtom.length; atom++) {
				double surplus = getAtomSurplus(atom);
				double d = 0.0;
				switch (corner) {
				case 0:	// top right
					d = mMaxX - 0.5 * (mMaxX + mMinY + mAtomX[atom] - mAtomY[atom]);
					break;
				case 1:	// bottom right
					d = mMaxX - 0.5 * (mMaxX - mMaxY + mAtomX[atom] + mAtomY[atom]);
					break;
				case 2:	// bottom left
					d = 0.5 * (mMinX + mMaxY + mAtomX[atom] - mAtomY[atom]) - mMinX;
					break;
				case 3:	// top left
					d = 0.5 * (mMinX - mMinY + mAtomX[atom] + mAtomY[atom]) - mMinX;
					break;
					}

				if (minDistance > d - surplus)
					minDistance = d - surplus;
				}

			return minDistance;
			}

		private double getAtomSurplus(int atom) {
			return (mMol.getAtomQueryFeatures(mAtom[atom]) != 0) ? 0.6
						  : (mMol.getAtomicNo(mAtom[atom]) != 6) ? 0.25 : 0.0;
			}

		protected void generateAtomListsOfFlipBonds(byte[] flipBondType) {
			
			}

		protected ArrayList<int[]> getCollisionList() {
			mCollisionPanalty = 0.0;
			ArrayList<int[]> collisionList = new ArrayList<int[]>();
			for (int i=1; i<mAtom.length; i++) {
				for (int j=0; j<i; j++) {
					double xdif = Math.abs(mAtomX[i]-mAtomX[j]);
					double ydif = Math.abs(mAtomY[i]-mAtomY[j]);
					double dist = Math.sqrt(xdif * xdif + ydif * ydif);
					if (dist < cCollisionLimitBondRotation) {
						int[] collidingAtom = new int[2];
						collidingAtom[0] = mAtom[i];
						collidingAtom[1] = mAtom[j];
						collisionList.add(collidingAtom);
						}
					double panalty = 1.0 - Math.min(dist, 1.0);
					mCollisionPanalty += panalty * panalty;
					}
				}
			return collisionList;
			}

		protected double getCollisionPanalty() {
			return mCollisionPanalty;
			}

		protected void locateBonds() {
			int fragmentBonds = 0;
			for (int i=0; i<mAtom.length; i++)
				for (int j=0; j<mConnAtoms[mAtom[i]]; j++)
					if (mMol.getConnAtom(mAtom[i], j) > mAtom[i])
						fragmentBonds++;

			mBond = new int[fragmentBonds];
			mAtomIndex = new int[mAtoms];

			fragmentBonds = 0;
			for (int i=0; i<mAtom.length; i++) {
				mAtomIndex[mAtom[i]] = i;
				for (int j=0; j<mConnAtoms[mAtom[i]]; j++) {
					if (mMol.getConnAtom(mAtom[i], j) > mAtom[i]) {
						mBond[fragmentBonds] = mMol.getConnBond(mAtom[i], j);
						fragmentBonds++;
						}
					}
				}
			}

		protected void optimizeAtomCoordinates(int atomIndex) {
			double x = mAtomX[atomIndex];
			double y = mAtomY[atomIndex];

			InventorAngle[] collisionForce = new InventorAngle[4];

			int forces = 0;
			for (int i=0; i<mBond.length; i++) {
				if (forces >= 4)
					break;

				if (atomIndex == mAtomIndex[mMol.getBondAtom(0, mBond[i])]
				 || atomIndex == mAtomIndex[mMol.getBondAtom(1, mBond[i])])
					continue;

				double x1 = mAtomX[mAtomIndex[mMol.getBondAtom(0, mBond[i])]];
				double y1 = mAtomY[mAtomIndex[mMol.getBondAtom(0, mBond[i])]];
				double x2 = mAtomX[mAtomIndex[mMol.getBondAtom(1, mBond[i])]];
				double y2 = mAtomY[mAtomIndex[mMol.getBondAtom(1, mBond[i])]];
				double d1 = Math.sqrt((x1-x)*(x1-x)+(y1-y)*(y1-y));
				double d2 = Math.sqrt((x2-x)*(x2-x)+(y2-y)*(y2-y));
				double bondLength = Math.sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1));

				if (d1<bondLength && d2<bondLength) {
					if (x1 == x2) {
						double d = Math.abs(x-x1);
						if (d<cCollisionLimitAtomMovement)
							collisionForce[forces++] = new InventorAngle(InventorAngle.getAngle(x1,y,x,y),
																		 (cCollisionLimitAtomMovement-d)/2);
						}
					else if (y1 == y2) {
						double d = Math.abs(y-y1);
						if (d<cCollisionLimitAtomMovement)
							collisionForce[forces++] = new InventorAngle(InventorAngle.getAngle(x,y1,x,y),
																		 (cCollisionLimitAtomMovement-d)/2);
						}
					else {
						double m1 = (y2-y1)/(x2-x1);
						double m2 = -1/m1;
						double a1 = y1-m1*x1;
						double a2 = y-m2*x;
						double xs = (a2-a1)/(m1-m2);
						double ys = m1*xs+a1;
						double d = Math.sqrt((xs-x)*(xs-x)+(ys-y)*(ys-y));
						if (d<cCollisionLimitAtomMovement)
							collisionForce[forces++] = new InventorAngle(InventorAngle.getAngle(xs,ys,x,y),
																		 (cCollisionLimitAtomMovement-d)/2);
						}
					continue;
					}

				if (d1<cCollisionLimitAtomMovement) {
					collisionForce[forces++] = new InventorAngle(InventorAngle.getAngle(x1,y1,x,y),
																 (cCollisionLimitAtomMovement-d1)/2);
					continue;
					}

				if (d2<cCollisionLimitAtomMovement) {
					collisionForce[forces++] = new InventorAngle(InventorAngle.getAngle(x2,y2,x,y),
																 (cCollisionLimitAtomMovement-d2)/2);
					continue;
					}
				}

			if (forces > 0) {
				InventorAngle force = CoordinateInventor.getMeanAngle(collisionForce, forces);
				mAtomX[atomIndex] += force.mLength * Math.sin(force.mAngle);
				mAtomY[atomIndex] += force.mLength * Math.cos(force.mAngle);
				}
			}
		}
	}

class InventorAngle {
	double mAngle;
	double mLength;

	protected static double getAngle(double x1, double y1, double x2, double y2) {
		double angle;
		double xdif = x2 - x1;
		double ydif = y2 - y1;

		if (ydif != 0) {
			angle = Math.atan(xdif/ydif);
			if (ydif < 0) {
				if (xdif < 0)
					angle -= Math.PI;
				else
					angle += Math.PI;
				}
			}
		else
			angle = (xdif >0) ? Math.PI/2 : -Math.PI/2;

		return angle;
		}

	protected InventorAngle(double angle, double length) {
		mAngle = angle;
		mLength = length;
		}

	protected InventorAngle(double x1, double y1, double x2, double y2) {
		mAngle = getAngle(x1, y1, x2, y2);
		double xdif = x2 - x1;
		double ydif = y2 - y1;
		mLength = Math.sqrt(xdif * xdif + ydif * ydif);
		}
	}


class InventorChain {
	protected int[] mAtom;
	protected int[] mBond;

	public InventorChain(int chainLength) {
		mAtom = new int[chainLength];
		mBond = new int[chainLength];
			// last mBond[] value isn't needed if chain isn't a ring
		}

	protected int getChainLength() {
		return mAtom.length;
		}

	protected int[] getRingAtoms() {
		return mAtom;
		}

	protected int[] getRingBonds() {
		return mBond;
		}
	}


//class FlipBondIterator
