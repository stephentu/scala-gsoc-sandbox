#!/bin/sh

for host in r{2,6,8,9,10,11,12,13,15,16,17,18,19,20,22,23,24,25,26,27,28,29,30,31,32,33,36,37,38,40}; do
cat <<EOF | ssh -o StrictHostKeyChecking=no sltu@$host.millennium.berkeley.edu "bash"
echo "HOST $host: STDOUT"
cat /scratch/sltu/$host-$1.stdout
EOF
done

