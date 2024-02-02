# htm.java

Unofficial open source htm.java version (Berlin Brown)

## Updates

Currently can build with Maven:

Will not use gradle

```text
Apache Maven 3.8.6

Test Compile with Java 11, compile to Java8

IDE Environment - Tested with Eclipse - Version: 2023-12 (4.30.0)

At project root

$ mvn clean package

or

$ mvn clean compile exec:java


Unit and Integration tests will run

```
<br>

#### [Official is Here](https://github.com/numenta/htm.java/issues/193)  **Java&trade;** version of...
## Hierarchical Temporal Memory [(HTM)](http://numenta.com/learn/principles-of-hierarchical-temporal-memory.html)

Based on official version, fork for simplicity

**Community-supported & ported from the** [Numenta Platform for Intelligent Computing (NuPIC) ](https://github.com/numenta/nupic) python project.

_**NOTE: Minimum JavaSE version is 8**_

<br>

## Versioning
(Tracked according to core algorithms)  

| Core Algorithm  | NuPIC Date    |HTM.Java Date | Latest NuPIC SHA | Latest HTM.Java SHA | Status|
| --------------- |:-------------:|:------------:|:----------------:|:-------------------:|:-----:|
| SpatialPooler   | 2016-12-11    | 2016-10-07   |[commit](https://github.com/numenta/nupic/commit/5c3edead9526d3b5fb6a4f37ad9d38cdcf32f5ff)|[commit](https://github.com/numenta/htm.java/commit/2cdcee1fcc5f6c18c2c48b4b553c49879c1256bb#diff-22f96ea06fd0c2b3593c755cbccf0a8b)| [*Behind NuPIC Merge #3411](https://github.com/numenta/nupic/pull/3411)
| TemporalMemory  | 2017-06-02    | 2016-10-13   |[commit](https://github.com/numenta/nupic/commit/b1f35fe15a1cbed689d1173cfcecddfab781baab)|[commit](https://github.com/numenta/htm.java/commit/7f4d8f2e2c910dd662909442546516e36adfc7cc)| [*Behind NuPIC Merge #3654](https://github.com/numenta/nupic/pull/3654)

\* May be one of: "Sync'd" or "Behind". "Behind" expresses a temporary lapse in synchronization while devs are implementing new changes.

<sub><sup>**NOTE:** "Behind" status does not imply _**any**_ lack of operational ability. The ```master``` branch of HTM.Java will always be _**fully**_ operational. 
<br>
Any fully critical feature addition to NuPIC will _**always**_ be matched (sync'd) ASAP, however due to ongoing updates, the algortithms within NuPIC may reach ahead for a short period of time while the HTM Community is busy porting the difference(s). We are committed to keeping HTM.Java up to date with NuPIC at _**all**_ times.</sup></sub>
***

## Project Goals

The primary goal of this library development is to provide a Java version of NuPIC that has a 1-to-1 correspondence to all systems, functionality and tests provided by Numenta's open source implementation; while observing the tenets, standards and conventions of Java language best practices and development.

By working closely with Numenta and receiving their enthusiastic support and guidance, it is intended that this library be maintained as a viable Java language alternative to Numenta's C++ and Python offerings. However it must be understood that "official" support is (for the time being) currently limited to community resources such as the maintainers of this library and Numenta Forums / Message Lists and IRC:

 * [NuPIC Community](http://numenta.org/)
 * [New HTM Forum](http://discourse.numenta.org)

***
## How Do I Get The Code? 

* **(A)** Instructions for developers who would like to contribute code back to the community.  (Fork)  
* **(B)** Instructions for those who would like to "fiddle around" with the code in thier own github repo.  (Clone)  
* **(C)** How to download Zipped or Tar'd Tagged Releases  (Download Zip or Tar)  

**A.** Developers who wish to make contributions are required to [Fork the htm.java repo](https://help.github.com/articles/fork-a-repo/) and then clone from their personal "Fork" of htm.java...
```
your_git_directory% git clone https://github.com/<your_github_username>/htm.java.git
```

**B.** Anybody who just wants to "play" with the code...
```
your_git_directory% git clone https://github.com/numenta/htm.java.git
```

**C.** [Proceed here](https://github.com/numenta/htm.java/releases) to download the latest tagged release (or older if you like)

_The instructions on the above link **(Fork the htm.java repo)** provide detail about how to fork github repos..._  

**In addition:** [a video is provided](https://www.youtube.com/watch?v=Yc3PKaT1knU) that explains Numenta's contributor rules and plenty of helpful tips on using git and other commands.

***

[license]:LICENSE.txt
[license img]:https://img.shields.io/badge/License-GNU%20Affero-blue.svg

[docs-badge]:https://img.shields.io/badge/API-docs-blue.svg?style=flat-square
[docs]:http://numenta.org/docs/htm.java/
