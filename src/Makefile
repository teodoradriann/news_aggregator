.PHONY: run clean build

build:
	mvn -f pom.xml clean package

run:
	mvn -f pom.xml exec:java -Dexec.mainClass="Main" -Dexec.args="4 /Users/adrian/Documents/facultate/an3/apd/tema1/checker/input/tests/test_1/articles.txt /Users/adrian/Documents/facultate/an3/apd/tema1/checker/input/tests/test_1/inputs.txt"

clean:
	rm *.txt && \
	mvn -f pom.xml clean