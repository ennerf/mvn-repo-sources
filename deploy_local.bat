call mvn -DaltDeploymentRepository=github.ennerf::default::file:mvn-repo/releases/ clean deploy
rmdir /s /q mvn-repo\releases\dummy
