-) mvn syntax for deploying an 3rd party jars into a local repo:
mvn deploy:deploy-file -Durl=file:mvn-repo/releases/ -Dfile=opencv-247.jar -DpomFile=pom.xml 

-) For more info see http://maven.apache.org/guides/mini/guide-3rd-party-jars-remote.html