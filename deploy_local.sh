#!/bin/bash
mvn -DaltDeploymentRepository=github.ennerf::default::file:mvn-repo/releases/ clean deploy