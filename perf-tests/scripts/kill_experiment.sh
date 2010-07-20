#!/bin/sh

for host in r{9,10,11,12,13,15,16,17,18,19,20}; do
cat <<EOF | ssh -o StrictHostKeyChecking=no sltu@$host.millennium.berkeley.edu "bash"
killall java
EOF
done

