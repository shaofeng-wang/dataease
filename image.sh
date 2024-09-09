# mvn clean package

export IMAGE_REPO=hospv3.prod.cdueduxun.cn:9443
export IMAGE_TAG=1.18.7
export IMAGE_BUILD=1015

docker images | grep '/dataease/dataease-server' | awk '{print $3}' | xargs docker rmi

docker buildx build \
  --platform=linux/amd64 \
  --build-arg IMAGE_TAG=$IMAGE_TAG \
  -t $IMAGE_REPO/dataease/dataease-server:$IMAGE_TAG.$IMAGE_BUILD \
  -o type=docker .

docker push $IMAGE_REPO/dataease/dataease-server:$IMAGE_TAG.$IMAGE_BUILD
