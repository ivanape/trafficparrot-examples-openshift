import java.util.Random;

// path of the template to use
// name of the template that will be created
def demoId = "finance-" + (10000 + new Random().nextInt(10000))
def trafficParrotId = "trafficparrot-" + (10000 + new Random().nextInt(10000))

// TODO:
//1. mount TP config
//1. deploy TP
//1. upload the file to import
//1. configure demo
//1. deploy demo
//1. manual gate
//1. clean up

// to mount the /mappings directory that is committed:
//oc create configmap game-config --from-file=example-files/

// NOTE, the "pipeline" directive/closure from the declarative pipeline syntax needs to include, or be nested outside,
// and "openshift" directive/closure from the OpenShift Client Plugin for Jenkins.  Otherwise, the declarative pipeline engine
// will not be fully engaged.
pipeline {
    agent any
    options {
        // set a timeout of 20 minutes for this pipeline
        timeout(time: 20, unit: 'MINUTES')
    }
    stages {
        stage('preamble') {
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withProject() {
                            echo "Using project: ${openshift.project()}"
                            echo "Using demo id: ${demoId}"
                        }
                    }
                }
            }
        }
        stage('create') {
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withProject() {
                            echo "Start build for: ${demoId}"
                            openshift.newApp("scripts/openshift/finance/build.json", "--name=${demoId}", "--param=APPLICATION_NAME=${demoId}")
                        }
                    }
                } // script
            } // steps
        } // stage
        stage('build') {
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withProject() {
                            echo "Waiting for build ${demoId} to finish..."
                            def builds = openshift.selector("bc", demoId).related('builds')
                            builds.untilEach(1) {
                                return (it.object().status.phase == "Complete")
                            }
                            echo "${demoId} has been built!"
                        }
                    }
                } // script
            } // steps
        } // stage
        stage('deploy-trafficparrot') {
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withProject() {
                            echo "Start deploy for: ${trafficParrotId}"
                            openshift.newApp("scripts/openshift/finance/build.json", "--name=${trafficParrotId}", "--param=APPLICATION_NAME=${trafficParrotId}")
                            openshift.selector("dc", trafficParrotId).rollout();
                            openshift.selector("dc", trafficParrotId).related('pods').untilEach(1) {
                                return (it.object().status.phase == "Running")
                            }
                        }
                    }
                } // script
            } // steps
        } // stage
//        stage('tag') {
//            steps {
//                script {
//                    openshift.withCluster() {
//                        openshift.withProject() {
//                            // if everything else succeeded, tag the ${demoId}:latest image as ${demoId}-staging:latest
//                            // a pipeline build config for the staging environment can watch for the ${demoId}-staging:latest
//                            // image to change and then deploy it to the staging environment
//                            openshift.tag("${demoId}:latest", "${demoId}-staging:latest")
//                        }
//                    }
//                } // script
//            } // steps
//        } // stage
    } // stages
    post {
        always {
            script {
                openshift.withCluster() {
                    openshift.withProject() {
                        openshift.selector("all", [ "app" : demoId ]).delete()
                        if (openshift.selector("secrets", demoId).exists()) {
                            openshift.selector("secrets", demoId).delete()
                        }

                        openshift.selector("all", [ "app" : trafficParrotId ]).delete()
                        if (openshift.selector("secrets", trafficParrotId).exists()) {
                            openshift.selector("secrets", trafficParrotId).delete()
                        }
                    }
                }
            } // script
        }
    }
} // pipeline