SCALAC=/home/stephentu/scala-gsoc/build/pack/bin/scalac
SCALA=/home/stephentu/scala-gsoc/build/pack/bin/scala
FILES=clients.scala server.scala
FLAGS=--numports=10 --numclientsperport=1 --numreqperclient=1

compile:
	mkdir -p classes
	$(SCALAC) -d classes $(FILES)

prepare-tests:
	mkdir -p results
	$(SCALA) -cp classes Server $(FLAGS) > results/server.out 2> results/server.err &

run-tests:
	$(SCALA) -cp classes Clients $(FLAGS)

clean:
	rm -rf classes
	rm -rf results
