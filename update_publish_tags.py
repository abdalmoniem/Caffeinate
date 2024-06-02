import os
import sys
from ruamel.yaml import YAML

if len(sys.argv) != 2:
    print('must supply git top level with `git rev-parse --show-toplevel`')
    exit(93)

pathName = os.path.abspath(sys.argv[1])
publishReleaseYamlPath = f'{pathName}/.github/workflows/publish_release.yml'

print(publishReleaseYamlPath)
tags = os.popen('git tag -l --sort=-v:refname').read().strip().split('\n')

# print('tags:', tags)

yaml = YAML(typ='rt')
# yaml.indent(mapping=2, offset=2)
yaml.preserve_quotes = True

# Read YAML file
with open(publishReleaseYamlPath, 'r') as yamlFile:
    dataLoaded = yaml.load(yamlFile)

# print(dataLoaded)

publishReleaseYamlFileName = os.path.basename(publishReleaseYamlPath)

print(f'tags in {publishReleaseYamlFileName}:', dataLoaded['on']['workflow_dispatch']['inputs']['releaseTag']['options'])

if (dataLoaded['on']['workflow_dispatch']['inputs']['releaseTag']['options'] != tags):
    dataLoaded['on']['workflow_dispatch']['inputs']['releaseTag']['options'] = tags

    print(f'updated tags in {publishReleaseYamlFileName} to:', dataLoaded['on']['workflow_dispatch']['inputs']['releaseTag']['options'])

    with open(publishReleaseYamlPath, 'w') as yamlFile:
        yaml.dump(dataLoaded, yamlFile)

    # yaml.dump(dataLoaded, sys.stdout)

    print('tags updated!')
else:
    print('tags are up to date!')