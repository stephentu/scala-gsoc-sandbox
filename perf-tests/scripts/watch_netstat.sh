#!/bin/sh

cat <<EOF | ssh -o StrictHostKeyChecking=no sltu@$1.millennium.berkeley.edu "bash"
netstat -aln | grep tcp | grep 16873
EOF

