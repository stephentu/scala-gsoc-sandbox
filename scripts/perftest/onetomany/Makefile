SCALAC=/home/stephentu/scala-gsoc/build/pack/bin/scalac
SCALA=/home/stephentu/scala-gsoc/build/pack/bin/scala
FILES=clients.scala server.scala
#FLAGS=--numclients=200 --numreqperclient=2000
FLAGS=--nio --numclients=200 --numreqperclient=2000

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
