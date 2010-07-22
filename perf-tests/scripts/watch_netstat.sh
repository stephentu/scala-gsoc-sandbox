#!/bin/sh

cat <<EOF | ssh -o StrictHostKeyChecking=no sltu@$1.millennium.berkeley.edu "bash"
netstat -aln | grep tcp | grep 16780
netstat -aln | grep tcp | grep 16781
netstat -aln | grep tcp | grep 16782
netstat -aln | grep tcp | grep 16783
netstat -aln | grep tcp | grep 16784
EOF

