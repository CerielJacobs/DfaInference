#! /usr/bin/python

import sys

sum = 0.0
count = 0

for line in sys.stdin:
    sum += float(line)
    count += 1

print sum / count
