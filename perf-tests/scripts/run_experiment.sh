#!/bin/sh

#for host in r{9,10,11,12,13,15,16,17,18,19,20}; do
for host in r{2,6,8,9,10,11,12,13,15,17,18,19,20,22,23,24,25,26,27,28,29,30,31,32,33,36,37,38,40}; do
cat <<EOF | ssh -o StrictHostKeyChecking=no sltu@$host.millennium.berkeley.edu "bash"
mkdir -p /scratch/sltu
echo '/work/sltu/scala-gsoc-sandbox/perf-tests/launch-node.sh $1 $2 1> /scratch/sltu/$host-$2.stdout 2> /scratch/sltu/$host-$2.stderr &'
/work/sltu/scala-gsoc-sandbox/perf-tests/launch-node.sh $1 $2 1> /scratch/sltu/$host-$2.stdout 2> /scratch/sltu/$host-$2.stderr &
EOF
done
