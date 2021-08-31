clean:
	mvn clean

build:
	mvn install

docker: build
	docker build -t stampy-app:latest stampy-app
