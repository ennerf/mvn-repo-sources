cd opencv
call mvn deploy:deploy-file -Durl=file:../mvn-repo/releases/ -Dfile=opencv-247.jar -DpomFile=pom.xml
cd ..
call mvn -DaltDeploymentRepository=github.ennerf::default::file:mvn-repo/releases/ clean deploy
