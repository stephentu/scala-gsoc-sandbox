SCALAC=/home/stephentu/scala-gsoc/build/pack/bin/scalac
SCALA=/home/stephentu/scala-gsoc/build/pack/bin/scala
FILES=client.scala server.scala controller.scala
#FLAGS=--numreqperclient=100
FLAGS=--nio --numreqperclient=100
PORTS=10002..10052

compile:
	mkdir -p classes
	$(SCALAC) -d classes $(FILES)

prepare-tests:
	mkdir -p results
#$(SCALA) -cp classes Server $(FLAGS) > results/server.out 2> results/server.err &
	for PORT in {$(PORTS)}; do \
		$(SCALA) -cp classes Client --port=$$PORT $(FLAGS) > results/client-$$PORT.out 2> results/client-$$PORT.err & \
	done

run-tests:
	$(SCALA) -cp classes Controller --ports=$(PORTS) $(FLAGS)

clean-results:
	rm -rf results

clean:
	rm -rf classes
	rm -rf results
