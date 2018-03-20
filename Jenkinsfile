def version = env.BRANCH_NAME

pipeline {
    agent none

    stages {
        stage("Review") {
            when {
                expression { env.CHANGE_ID != null }
            }
            parallel {
                stage("StaticAnalysis") {
                    steps {
                        node("slave-sbt") {
                            checkout scm
                            sh 'sbt clean scalafmtCheck scalafmtSbtCheck scapegoat'
                        }
                    }
                }
                stage("Tests/Coverage") {
                    steps {
                        node("slave-sbt") {
                            checkout scm
                            sh 'sbt clean coverage test coverageReport coverageAggregate'
                        }
                    }
                }
            }
        }
        stage("Release") {
            when {
                expression { env.CHANGE_ID == null }
            }
            steps {
                node("slave-sbt") {
                    checkout scm
                    sh 'sbt releaseEarly universal:packageZipTarball'
                    stash name: "service", includes: "modules/service/target/universal/admin-service-*.tgz"
                }
            }
        }
        stage("Build Image") {
            when {
                expression { version ==~ /v\d+\.\d+\.\d+.*/ }
            }
            steps {
                node("slave-sbt") {
                    unstash name: "service"
                    sh "mv modules/service/target/universal/admin-service-*.tgz ./admin-service.tgz"
                    sh "oc start-build admin-build --from-file=admin-service.tgz --follow"
                    openshiftTag srcStream: 'admin', srcTag: 'latest', destStream: 'admin', destTag: version.substring(1), verbose: 'false'
                }
            }
        }
    }
}