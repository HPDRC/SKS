# Open Source Spatial Keyword Search at TerraFly. ALPHA Release.

The TerraFly Spatial Keyword Search (SKS) is an Open Source System which provides a method to efficiently compute spatial queries with conjunctive Boolean constraints on the textual content of the records. SKS algorithms combine an R-tree data structure with an inverted index through inclusion of spatial references in the posting lists queried. The result is a disk-resident, dual-index data structure that is used to proactively reduce the search space. R-tree nodes are visited in best matching order. A node entry is placed in the priority queue if there exists, at least one object that satisfies the Boolean condition within the sub-tree pointed to by the entry; otherwise, the sub-tree is not explored further. This method provides improved performance over alternative techniques and scales to a large number of objects. The system has been extensively tested with real-world spatial databases.
#### Downloads:
For Microsoft Windows: [sks_Windows.zip](https://github.com/HPDRC/SKS/files/2618497/sks_Windows.zip)
For Linux / Unix: [sks_LinuxUnix.tar.gz](https://github.com/HPDRC/SKS/files/2618495/sks_LinuxUnix.tar.gz)
For Apple Mac OS: [sks_MacOS.tar.gz](https://github.com/HPDRC/SKS/files/2618496/sks_MacOS.tar.gz)
Source Code (In compressed file format):
* [sksSourceCode.zip](https://github.com/HPDRC/SKS/files/2618499/sksSourceCode.zip) (.zip format)
* [sksSourceCode.tar.gz](https://github.com/HPDRC/SKS/files/2618498/sksSourceCode.tar.gz) (tar.gz format)

The user may utilize their datasets and/or the following datasets that we provide: http://n00.cs.fiu.edu/Datasets/

#### System Manual:
TBA

#### User Manual for Query Composition:
cake.fiu.edu/OpenSKS/sks-query.manual.html

#### Algorithm Papers:
Ariel Cary, Ouri Wolfson, Naphtali Rishe. “Efficient and Scalable Method for Processing Top-k Spatial Boolean Queries.” Proceedings of 22nd International Conference on Scientific and Statistical Database Management. Published as: Lecture Notes in Computer Science, Volume 6187/2010: Scientific and Statistical Database Management. Springer Verlag, Berlin / Heidelberg, 2010. Pages 87-95. (CAKE.fiu.edu/OpenSKS/Cary+al-10-TK.Top-K.Spatial.Boolean.Queries.camera_ready.pdf)

Ariel Cary, Zhengguo Sun, Vagelis Hristidis, Naphtali Rishe. “Experiences on Processing Spatial Data with MapReduce.” in Springer Lecture Notes in Computer Science, Volume 5566/2009: Scientific and Statistical Database Management. (Proceedings of the 21st International Conference on Scientific and Statistical Database Management. New Orleans, Louisiana, USA. June 1-5, 2009.) pp. 302-319.

Naphtali Rishe, Vagelis Hristidis, Raju Rangaswami, Ouri Wolfson, Howard Ho, Ariel Cary, Zhengguo Sun, Lester Melendes. “Indexing Geospatial Data with MapReduce”. NSF CLuE PI Conference, Palo Alto, October 2009.

Ian De Felipe, Vagelis Hristidis, Naphtali Rishe. “Keyword Search on Spatial Databases.” Proceedings of the 2008 IEEE International Conference on Data Engineering. Cancun, Mexico. April 7-12, 2008. pp. 656-665.

#### License:
This System is distributed as Open Source under the BSD License

Copyright (c) 2011-2013, TerraFly.com and Florida International University (FIU). All rights reserved. Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
  * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
  * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
  * Neither the name of the organization nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS “AS IS” AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL TerraFly or FIU BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

Project Director: Naphtali Rishe.