// FinSecure Pay CI/CD: Checkout -> Build -> Test -> Package -> Security Checks -> Container Build -> Push -> Deploy
pipeline {
    agent any

    parameters {
        choice(name: 'ACTION', choices: ['deploy', 'rollback'], description: 'deploy a new build, or roll back to the previous revision')
        choice(name: 'TARGET', choices: ['minikube', 'cloud'], description: 'minikube: load image into local cluster; cloud: pull from registry (EKS/AKS/GKE)')
        booleanParam(name: 'PUSH_IMAGE', defaultValue: false, description: 'Push the image to the registry (needs the dockerhub-credentials Jenkins credential)')
        string(name: 'NOTIFY_EMAIL', defaultValue: 'devops@finsecure.local', description: 'Build notification recipient')
    }

    environment {
        IMAGE_REPO = 'docker.io/badri3590/finsecure-pay'
        VERSION    = "1.0.${BUILD_NUMBER}"
        K8S_NS     = 'finsecure'
        TRIVY_ARGS = '--severity HIGH,CRITICAL --ignore-unfixed --timeout 20m --no-progress'
    }

    options {
        timestamps()
        timeout(time: 40, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'mkdir -p reports'
            }
        }

        stage('Rollback') {
            when { expression { params.ACTION == 'rollback' } }
            steps {
                sh '''
                    kubectl -n $K8S_NS rollout history deploy/finsecure-pay
                    kubectl -n $K8S_NS rollout undo deploy/finsecure-pay
                    kubectl -n $K8S_NS rollout status deploy/finsecure-pay --timeout=180s
                    kubectl -n $K8S_NS get deploy finsecure-pay -o jsonpath='{.spec.template.spec.containers[0].image}{"\\n"}'
                '''
            }
        }

        stage('Release') {
            when { expression { params.ACTION == 'deploy' } }
            stages {
                stage('Build') {
                    steps { sh 'mvn -B -DskipTests clean package' }
                }

                stage('Test') {
                    steps { sh 'mvn -B test' }
                    post {
                        always { junit 'target/surefire-reports/*.xml' }
                    }
                }

                stage('Package') {
                    steps { archiveArtifacts artifacts: 'target/*.jar', fingerprint: true }
                }

                stage('Security Checks') {
                    parallel {
                        stage('Static analysis (SpotBugs)') {
                            steps { sh 'mvn -B -Pquality -DskipTests verify' }
                        }
                        stage('Dependency + secret scan (Trivy)') {
                            steps {
                                // Gate: any HIGH/CRITICAL finding in dependencies or committed secrets fails the build
                                sh '''#!/bin/bash
                                    set -o pipefail
                                    trivy fs --scanners vuln,secret $TRIVY_ARGS --exit-code 1 . | tee reports/trivy-fs.txt
                                '''
                            }
                        }
                    }
                }

                stage('Container Build') {
                    steps {
                        sh 'docker build --build-arg APP_VERSION=$VERSION -t $IMAGE_REPO:$VERSION -t $IMAGE_REPO:latest .'
                    }
                }

                stage('Image Scan (Trivy)') {
                    steps {
                        sh '''#!/bin/bash
                            set -o pipefail
                            trivy image --scanners vuln $TRIVY_ARGS --exit-code 1 $IMAGE_REPO:$VERSION | tee reports/trivy-image.txt
                        '''
                    }
                }

                stage('Push') {
                    when { expression { params.PUSH_IMAGE } }
                    steps {
                        withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials',
                                usernameVariable: 'REG_USER', passwordVariable: 'REG_PASS')]) {
                            sh '''
                                echo "$REG_PASS" | docker login -u "$REG_USER" --password-stdin
                                # multi-arch (amd64 for EKS nodes, arm64 for Apple Silicon) published as one tag
                                docker buildx inspect multi >/dev/null 2>&1 || docker buildx create --name multi --driver docker-container
                                docker buildx build --builder multi --platform linux/amd64,linux/arm64 \
                                    --build-arg APP_VERSION=$VERSION -t $IMAGE_REPO:$VERSION -t $IMAGE_REPO:latest --push .
                                docker logout
                            '''
                        }
                    }
                }

                stage('Deploy') {
                    steps {
                        sh '''#!/bin/bash
                            set -e
                            if [ "$TARGET" = "minikube" ]; then minikube image load $IMAGE_REPO:$VERSION; fi
                            if [ "$TARGET" = "cloud" ]; then export KUBECONFIG=$HOME/.kube/eks-config AWS_PROFILE=fsp AWS_REGION=us-east-1; fi
                            kubectl apply -f k8s/namespace.yaml -f k8s/configmap.yaml -f k8s/service.yaml -f k8s/pdb.yaml
                            kubectl -n $K8S_NS get deploy finsecure-pay >/dev/null 2>&1 || \
                                sed "s|image: .*|image: $IMAGE_REPO:$VERSION|" k8s/deployment.yaml | kubectl apply -f -
                            kubectl -n $K8S_NS set image deploy/finsecure-pay finsecure-pay=$IMAGE_REPO:$VERSION
                            if ! kubectl -n $K8S_NS rollout status deploy/finsecure-pay --timeout=180s; then
                                echo "Rollout failed - rolling back"
                                kubectl -n $K8S_NS rollout undo deploy/finsecure-pay
                                kubectl -n $K8S_NS rollout status deploy/finsecure-pay --timeout=180s
                                exit 1
                            fi
                            kubectl -n $K8S_NS get pods -o wide
                        '''
                    }
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'reports/*', allowEmptyArchive: true
        }
        success {
            emailext to: params.NOTIFY_EMAIL, subject: "[FinSecure] SUCCESS #${BUILD_NUMBER} (${params.ACTION})",
                    body: "Build ${BUILD_URL} succeeded. Version ${VERSION}."
        }
        failure {
            emailext to: params.NOTIFY_EMAIL, subject: "[FinSecure] FAILED #${BUILD_NUMBER} (${params.ACTION})",
                    body: "Build ${BUILD_URL} failed. Check ${BUILD_URL}console. No deployment was performed for failed gates."
        }
    }
}
