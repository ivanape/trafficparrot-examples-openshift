import java.util.Random;

// path of the template to use
// name of the template that will be created
def templateName = "finance" + (10000 + new Random().nextInt(10000))

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
    agent {}
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
                        }
                    }
                }
            }
        }
        stage('cleanup') {
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withProject() {
                            // delete everything with this template label
                            openshift.selector("all", [ template : templateName ]).delete()
                            // delete any secrets with this template label
                            if (openshift.selector("secrets", templateName).exists()) {
                                openshift.selector("secrets", templateName).delete()
                            }
                        }
                    }
                } // script
            } // steps
        } // stage
        stage('create') {
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withProject() {
                            // create a new application from the templatePath
                            openshift.newApp(templatePath, "--name=" + templateName)
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
                            def builds = openshift.selector("bc", templateName).related('builds')
                            builds.untilEach(1) {
                                return (it.object().status.phase == "Complete")
                            }
                        }
                    }
                } // script
            } // steps
        } // stage
        stage('deploy') {
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withProject() {
                            def rm = openshift.selector("dc", templateName).rollout()
                            openshift.selector("dc", templateName).related('pods').untilEach(1) {
                                return (it.object().status.phase == "Running")
                            }
                        }
                    }
                } // script
            } // steps
        } // stage
        stage('tag') {
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withProject() {
                            // if everything else succeeded, tag the ${templateName}:latest image as ${templateName}-staging:latest
                            // a pipeline build config for the staging environment can watch for the ${templateName}-staging:latest
                            // image to change and then deploy it to the staging environment
                            openshift.tag("${templateName}:latest", "${templateName}-staging:latest")
                        }
                    }
                } // script
            } // steps
        } // stage
    } // stages
} // pipeline