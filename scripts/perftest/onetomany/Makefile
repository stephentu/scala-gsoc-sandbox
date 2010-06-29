SCALAC=/home/stephentu/scala-gsoc/build/pack/bin/scalac
SCALA=/home/stephentu/scala-gsoc/build/pack/bin/scala
FILES=clients.scala server.scala
#FLAGS=--numclients=300 --numreqperclient=1000
FLAGS=--nio --numclients=300 --numreqperclient=1000

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
