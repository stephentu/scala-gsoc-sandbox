#!/bin/sh

for host in r{9,10,11,12,13,15,16,17,18,19,20}; do
cat <<EOF | ssh -o StrictHostKeyChecking=no sltu@$host.millennium.berkeley.edu "bash"
mkdir -p /scratch/sltu
/work/sltu/scala-gsoc-sandbox/perf-tests/launch-node-nio.sh $1 $2 1> /scratch/sltu/$host-$2.stdout 2> /scratch/sltu/$host-$2.stderr &
EOF
done
