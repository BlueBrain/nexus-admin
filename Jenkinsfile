String version = env.BRANCH_NAME
Boolean isRelease = version ==~ /v\d+\.\d+\.\d+.*/
Boolean isPR = env.CHANGE_ID != null

pipeline {
    agent { label 'slave-sbt' }

    stages {
        stage("Review") {
            when {
                expression { isPR }
            }
            parallel {
                stage("StaticAnalysis") {
                    steps {
                        node("slave-sbt") {
                            checkout scm
                            sh 'sbt clean scalafmtCheck scalafmtSbtCheck test:scalafmtCheck compile test:compile scapegoat'
                        }
                    }
                }
                stage("Tests/Coverage") {
                    steps {
                        node("slave-sbt") {
                            checkout scm
                            sh "sbt clean coverage test coverageReport coverageAggregate"
                            sh "curl -s https://codecov.io/bash >> ./coverage.sh"
                            sh "bash ./coverage.sh -t `oc get secrets codecov-secret --template='{{.data.nexus_admin}}' | base64 -d`"
                        }
                    }
                }
            }
        }
        stage("Build Snapshot & Deploy") {
            when {
                expression { !isPR && !isRelease }
            }
            steps {
                checkout scm
                sh 'sbt releaseEarly universal:packageZipTarball'
                sh "mv modules/service/target/universal/admin-service-*.tgz ./admin-service.tgz"
                sh "oc start-build admin-build --from-file=admin-service.tgz --follow"
                sh "oc scale statefulset admin --replicas=0 --namespace=bbp-nexus-dev"
                sleep 2
                sh "oc scale statefulset admin --replicas=1 --namespace=bbp-nexus-dev"
                sleep 90 // services can take about 2 minutes to be up and running
                openshiftVerifyService namespace: 'bbp-nexus-dev', svcName: 'admin', verbose: 'false'
                build job: 'nexus/nexus-tests/master', parameters: [booleanParam(name: 'run', value: true)], wait: true
            }
        }
        stage("Build & Publish Release") {
            when {
                expression { isRelease }
            }
            steps {
                checkout scm
                sh 'sbt releaseEarly universal:packageZipTarball'
                sh "mv modules/service/target/universal/admin-service-*.tgz ./admin-service.tgz"
                sh "oc start-build admin-build --from-file=admin-service.tgz --follow"
                openshiftTag srcStream: 'admin', srcTag: 'latest', destStream: 'admin', destTag: version.substring(1), verbose: 'false'
            }
        }
        stage("Report Coverage") {
            when {
                expression { !isPR }
            }
            steps {
                checkout scm
                sh "sbt clean coverage test coverageReport coverageAggregate"
                sh "curl -s https://codecov.io/bash >> ./coverage.sh"
                sh "bash ./coverage.sh -t `oc get secrets codecov-secret --template='{{.data.nexus_admin}}' | base64 -d`"
            }
            post {
                always {
                    junit 'target/test-reports/TEST*.xml'
                }
            }
        }
    }
}