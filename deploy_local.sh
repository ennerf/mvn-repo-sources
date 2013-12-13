#!/bin/bash
mvn deploy:deploy-file -Durl=file:../mvn-repo/releases/ -Dfile=opencv-247.jar -DpomFile=pom.xml 
rm -rf ./mvn-repo/releases/dummy