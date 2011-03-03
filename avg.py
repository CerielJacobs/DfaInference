#! /usr/bin/python

import sys

sum = 0.0
count = 0

for line in sys.stdin:
    sum += float(line)
    count += 1

foo_string = '%2.4f' % (sum*100/count)

print foo_string
