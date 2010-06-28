SCALAC=/home/stephentu/scala-gsoc/build/pack/bin/scalac
SCALA=/home/stephentu/scala-gsoc/build/pack/bin/scala
FILES=a.scala b.scala controller.scala
#FLAGS=--num=10000
FLAGS=--nio --num=10000
#JAVA_OPTS=-agentpath:/home/stephentu/yjp-9.0.5/bin/linux-x86-32/libyjpagent.so

compile:
	mkdir -p classes
	$(SCALAC) -d classes $(FILES)

prepare-tests:
	mkdir -p results
	$(SCALA) -cp classes A $(FLAGS) > results/a.out 2> results/a.err &
	$(SCALA) -cp classes B $(FLAGS) > results/b.out 2> results/b.err &

run-tests:
	$(SCALA) -cp classes Controller $(FLAGS)
	tail -f results/a.out

clean:
	rm -rf classes
	rm -rf results
