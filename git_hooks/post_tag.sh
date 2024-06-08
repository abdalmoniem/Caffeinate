#!/bin/bash

if [ "$1" ]; then
   if git tag "$1" &>/dev/null; then
      gitTopLevel=$(git rev-parse --show-toplevel)

      if python "$gitTopLevel/git_hooks/update_publish_tags.py" "$gitTopLevel"; then
         echo "adding '$gitTopLevel/.github/workflows/publish_release.yml' to git staging..."

         git add "$gitTopLevel/.github/workflows/publish_release.yml"

         git commit --amend --no-edit
      fi;
   else
      echo fatal: tag "'$1'" already exists
   fi;

   exit 93
else
   git tag
fi;