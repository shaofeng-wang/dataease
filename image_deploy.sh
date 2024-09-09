export IMAGE_REPO=hospv3.test.yiyunhp.com:9443
export IMAGE_TAG=1.18.7
export IMAGE_BUILD=1011

docker ps | grep '/dataease/dataease-server' | awk '{print $1}' | xargs docker stop | xargs docker rm

docker images | grep '/dataease/dataease-server' | awk '{print $3}' | xargs docker rmi

docker pull $IMAGE_REPO/dataease/dataease-server:$IMAGE_TAG.$IMAGE_BUILD

docker run -p 8081:8081 -v `pwd`/conf:/opt/dataease/conf -v `pwd`/data:/opt/dataease/data -dit --name dataease_server $IMAGE_REPO/dataease/dataease-server:$IMAGE_TAG.$IMAGE_BUILD
