# Bundled Maven dependency

This repository contains an unchanged copy of ../algorithmfoundry-shade-culled-1.3.jar
in Maven repository layout, plus its artifact POM. The copy retains the original
archive's embedded notices and licenses.

SDRClassifier uses custom addRow/addCol methods absent from standard MTJ.
The root build resolves this artifact locally, avoiding an unpublished Central
dependency and a systemPath in the installed htm.java POM. Build/install the
root project before building the workbench on each new machine. Preserve the
original libs JAR for existing IDE configurations.
