#!/bin/sh

cat <<EOF | ssh -o StrictHostKeyChecking=no sltu@$1.millennium.berkeley.edu "bash"
tail -f /scratch/sltu/$2-port_$3.stdout
EOF

