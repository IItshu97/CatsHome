pipeline {
    agent any

    tools {
        maven 'Maven 3.8'
        jdk 'Java 21'
    }

    environment {
        GITEA_URL = 'gitea:3000'

        IMAGE_NAME = 'user/spring-boot-app'
        IMAGE_TAG = "${env.BUILD_ID}"
        FULL_IMAGE_PATH = "${GITEA_URL}/${IMAGE_NAME}:${IMAGE_TAG}"

        DOCKER_HOST = "unix:///var/run/docker.sock"

        REGISTRY_CREDS_ID = 'gitea-registry-creds'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Unit Tests') {
            steps {
                sh 'mvn clean compile test'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Integration & System Tests') {
            steps {
                sh 'mvn verify -DskipUnitTests'
            }
            post {
                always {
                    junit '**/target/failsafe-reports/*.xml'
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    dockerImage = docker.build("${FULL_IMAGE_PATH}")
                }
            }
        }

        stage('Push to Gitea Registry') {
            steps {
                script {
                    docker.withRegistry("http://${GITEA_URL}", "${REGISTRY_CREDS_ID}") {
                        dockerImage.push()
                        dockerImage.push('latest')
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Success! Image ${FULL_IMAGE_PATH} is available in Gitea Container Registry."
        }
        failure {
            echo "Failure! Check logs."
        }
    }
}
