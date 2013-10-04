#!/bin/sh
#JAVA_HOME="/cygdrive/d/Program Files/Java/jdk1.7.0_15"
export JAVA_HOME="/cygdrive/d/Program Files/Java/jdk1.6.0_24"
export MAVEN_HOME=`PWD`/apache-maven-3.0.4
export PATH=$MAVEN_HOME/bin:$JAVA_HOME/bin:$PATH
chmod u+x $MAVEN_HOME/bin/mvn

