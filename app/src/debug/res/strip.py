import os
import sys

if len(sys.argv) != 2:
   print('values folder not provided')
   exit(93)

stringsFileXML = os.path.join(os.path.abspath(sys.argv[1]), 'strings.xml')

print(stringsFileXML)

with open(stringsFileXML, encoding='utf8') as file:
   lines = file.readlines()

finalLines = []
startDelete = False

for line in lines:
   if 'app_name' in line:
      startDelete = True
      finalLines.append(line)
      continue

   if not startDelete or '</resources>' in line:
      finalLines.append(line)

print(''.join(finalLines))

with open(stringsFileXML, 'w', encoding='utf8') as file:
   file.write(''.join(finalLines))