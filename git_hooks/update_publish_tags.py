import os
import git
import sys
from ruamel.yaml import YAML

if len(sys.argv) != 2:
    # noinspection SpellCheckingInspection
    print('must supply git top level with `git rev-parse --show-toplevel`')
    exit(93)

gitRepoTopLevelAbsPath = os.path.abspath(sys.argv[1])
gitRepo = git.Repo(gitRepoTopLevelAbsPath)
publishReleaseYamlPath = f'{gitRepoTopLevelAbsPath}/.github/workflows/publish_release.yml'

# noinspection SpellCheckingInspection
tags = gitRepo.git.tag('-l', '--sort=-v:refname').strip().split('\n')

yaml = YAML(typ='rt')
# yaml.indent(mapping=2, offset=2)
yaml.preserve_quotes = True

# Read YAML file
with open(publishReleaseYamlPath, 'r') as yamlFile:
    dataLoaded = yaml.load(yamlFile)

publishReleaseYamlFileName = os.path.basename(publishReleaseYamlPath)

if dataLoaded['on']['workflow_dispatch']['inputs']['releaseTag']['options'] != tags:
    print(f'tags in "{publishReleaseYamlFileName}":', dataLoaded['on']['workflow_dispatch']['inputs']['releaseTag']['options'])

    dataLoaded['on']['workflow_dispatch']['inputs']['releaseTag']['options'] = tags

    with open(publishReleaseYamlPath, 'w') as yamlFile:
        yaml.dump(dataLoaded, yamlFile)

    print(f'updated tags in "{publishReleaseYamlFileName}" to:', dataLoaded['on']['workflow_dispatch']['inputs']['releaseTag']['options'])

    # yaml.dump(dataLoaded, sys.stdout)

    print('tags updated!')
    exit(0)
else:
    print(f'tags in "{publishReleaseYamlFileName}" are up to date!')
    exit(93)