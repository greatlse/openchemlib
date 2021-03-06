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

package com.actelion.research.chem.descriptor;

public interface DescriptorConstants {
    public static final int DESCRIPTOR_TYPE_UNKNOWN = -1;
    public static final int DESCRIPTOR_TYPE_MOLECULE = 1;
    public static final int DESCRIPTOR_TYPE_REACTION = 2;

    public static final DescriptorInfo DESCRIPTOR_FFP512 = 
                            new DescriptorInfo("FragmentFingerprint512",
                                               "FragFp",
                                               DESCRIPTOR_TYPE_MOLECULE,
                                               true,
                                               true,
                                               false);
    public static final DescriptorInfo DESCRIPTOR_PFP512 = 
                            new DescriptorInfo("PathFingerprint512",
                                               "PathFp",
                                               DESCRIPTOR_TYPE_MOLECULE,
                                               true,
                                               true,
                                               false);
    public static final DescriptorInfo DESCRIPTOR_HashedCFp = 
                            new DescriptorInfo("HashedSphericalFingerprint512",
                                               "SphereFp",
                                               DESCRIPTOR_TYPE_MOLECULE,
                                               true,
                                               true,
                                               true);	// for the creation of up/down bonds
    public static final DescriptorInfo DESCRIPTOR_SkeletonSpheres = 
                                               new DescriptorInfo("HashedSkeletonSphereCount1024",
                                               "SkelSpheres",
                                               DESCRIPTOR_TYPE_MOLECULE,
                                               false,
                                               true,
                                               true);	// for the creation of up/down bonds
    public static final DescriptorInfo DESCRIPTOR_OrganicFunctionalGroups = 
    										   new DescriptorInfo("FunctionalGroupTreeCount1024",
    										   "OrgFunctions",
    										   DESCRIPTOR_TYPE_MOLECULE,
    										   false,
    										   false,
    										   true);	// for the creation of up/down bonds
    public static final DescriptorInfo DESCRIPTOR_CenteredSkeletonFragments = 
										        new DescriptorInfo("CenteredSkeletonFragments",
										        "CentSkelFrags",
										        DESCRIPTOR_TYPE_MOLECULE,
										        false,
										        true,
										        true);	// for the creation of up/down bonds
    public static final DescriptorInfo DESCRIPTOR_TopoPPHistDist = 
                            new DescriptorInfo("TopologicalPharmacophoreHistograms",
                                               "TopPPHist",
                                               DESCRIPTOR_TYPE_MOLECULE,
                                               false,
                                               false,
                                               false);
    public static final DescriptorInfo DESCRIPTOR_Flexophore = 
                            new DescriptorInfo("Flexophore",
                                               "Flexophore",
                                               DESCRIPTOR_TYPE_MOLECULE,
                                               false,
                                               false,
                                               false);
    public static final DescriptorInfo DESCRIPTOR_FlexophoreV3 =
            new DescriptorInfo("FlexophoreV3",
                    "FlexophoreV3",
                    DESCRIPTOR_TYPE_MOLECULE,
                    false,
                    false,
                    false);
    public static final DescriptorInfo DESCRIPTOR_FlexophoreV4 =
            new DescriptorInfo("FlexophoreV4",
                    "FlexophoreV4",
                    DESCRIPTOR_TYPE_MOLECULE,
                    false,
                    false,
                    false);
    public static final DescriptorInfo DESCRIPTOR_Flexophore_HighRes =
        					new DescriptorInfo("FlexophoreHighResolution",
        									   "FlexophoreHighRes",
        									   DESCRIPTOR_TYPE_MOLECULE,
        									   false,
        									   false,
        									   false);
    public static final DescriptorInfo DESCRIPTOR_ReactionIndex = 
                            new DescriptorInfo("ReactionIndex",
                                               "RxnIdx",
                                               DESCRIPTOR_TYPE_REACTION,
                                               false,
                                               false,
                                               false);
    public static final DescriptorInfo DESCRIPTOR_IntegerVector = 
    						new DescriptorInfo("IntegerVector",
    											"IntVec",
    											DESCRIPTOR_TYPE_UNKNOWN,
    											false,
    											false,
    											false);
   
    public static final DescriptorInfo DESCRIPTOR_MAX_COMMON_SUBSTRUCT = 
        					new DescriptorInfo("MaximumCommonSubstructure",
        										"Structure",
        										DESCRIPTOR_TYPE_MOLECULE,
        										false,
        										true,
        										false);

    public static final DescriptorInfo DESCRIPTOR_SUBSTRUCT_QUERY_IN_BASE = 
							new DescriptorInfo("SubStructureQueryInBase",
								"SSSQinB",
								DESCRIPTOR_TYPE_MOLECULE,
								false,
								false, // ??? TODO check
								false);
    
    public static final DescriptorInfo DESCRIPTOR_FULL_FRAGMENT_SET = 
            new DescriptorInfo("FullFragmentSet",
                               "FullFragSet",
                               DescriptorConstants.DESCRIPTOR_TYPE_MOLECULE,
                               true,
                               true,
                               false);
    
    public static final DescriptorInfo DESCRIPTOR_PhysicoChemicalProperties = 
            new DescriptorInfo("DescriptorPhysicoChemicalProperties",
                               "PhysChem",
                               DescriptorConstants.DESCRIPTOR_TYPE_MOLECULE,
                               false,
                               false,
                               false);

    public static final DescriptorInfo[] DESCRIPTOR_LIST = {
                                                DESCRIPTOR_FFP512,
                                                DESCRIPTOR_PFP512,
                                                DESCRIPTOR_HashedCFp,
                                                DESCRIPTOR_SkeletonSpheres,
                                                DESCRIPTOR_OrganicFunctionalGroups,
                                                DESCRIPTOR_Flexophore
                                                };

    public static final DescriptorInfo[] DESCRIPTOR_EXTENDED_LIST = {
                                                DESCRIPTOR_FFP512,
                                                DESCRIPTOR_PFP512,
                                                DESCRIPTOR_HashedCFp,
                                                DESCRIPTOR_SkeletonSpheres,
                                                DESCRIPTOR_CenteredSkeletonFragments,
                                                DESCRIPTOR_FULL_FRAGMENT_SET,
                                                DESCRIPTOR_MAX_COMMON_SUBSTRUCT,
                                                DESCRIPTOR_SUBSTRUCT_QUERY_IN_BASE,
                                                DESCRIPTOR_TopoPPHistDist,
                                                DESCRIPTOR_OrganicFunctionalGroups,
                                                DESCRIPTOR_Flexophore,
                                                DESCRIPTOR_Flexophore_HighRes,
                                                DESCRIPTOR_ReactionIndex,
                                                DESCRIPTOR_IntegerVector,
                                                DESCRIPTOR_FULL_FRAGMENT_SET,
                                                DESCRIPTOR_PhysicoChemicalProperties
                                                };
    }

