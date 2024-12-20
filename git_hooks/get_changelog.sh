#!/bin/bash

gitTopLevel="$(git rev-parse --show-toplevel)"
versionCodeFilter="\(versionCode\s\+=\s\+\)\([[:digit:]]\+\)"
tag="HEAD"
referenceTag=$(git describe --tags $(git rev-list --tags --max-count=1))
changelogsPath="$gitTopLevel/fastlane/metadata/android/en-US/changelogs"
changelogs=0
subjects=()
bodies=()

isWriteChanges=false
isCommitChanges=false

help() {
    echo "Usage: $0 [--tag <tag>] [--reference_tag <tag>] [--write_changes] [--commit_changes]"
    echo
    echo "Arguments:"
    echo "  --tag <tag>            : Tag to compare against the reference tag. If not provided, HEAD is used"
    echo "  --reference_tag <tag>  : Reference tag to compare against. If not provided, the latest tag is used."
    echo "  --write_changes        : Write the changes to a changelog file. The changelog file name is the <versionCode>.txt"
    echo "  --commit_changes       : Commit the changes to the repository."

    exit 1
}

# Parse arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --write_changes)
        isWriteChanges=true
        shift
        ;;
    --commit_changes)
      isCommitChanges=true
      shift
      ;;
    --reference_tag)
      if [[ -n "$2" ]]; then
          referenceTag="$2"
          shift 2
      else
          echo "Error: --reference_tag requires a value."
          echo
          help
      fi
      ;;
    --tag)
      if [[ -n "$2" ]]; then
          tag="$2"
          shift 2
      else
          echo "Error: --tag requires a value."
          echo
          help
      fi
      ;;
    --help)
      help
      ;;
    *)
      echo "Unknown option: $1"
      echo
      help
      ;;
  esac
done

commitHashesBetweenTags=$(git log "$referenceTag".."$tag" --pretty=format:"%h")
commitHashCount=$(echo "$commitHashesBetweenTags" | wc -l)
referenceVersionCode=$(git show "$referenceTag:app/build.gradle.kts" | grep versionCode | sed -e "s/$versionCodeFilter/\2/" | xargs)
tagVersionCode=$(git show "$tag:app/build.gradle.kts" | grep versionCode | sed -e "s/$versionCodeFilter/\2/" | xargs)

echo "Reference Tag: $referenceTag, referenceVersionCode: $referenceVersionCode"
echo "Tag: $tag, tagVersionCode: $tagVersionCode"
echo "processing $commitHashCount commits..."

if [ ! -d "$changelogsPath" ]; then
  echo "Creating changelogs folder..."
  mkdir -p "$changelogsPath"
fi

if [[ $commitHashCount -gt 0 && -f "$changelogsPath/$tagVersionCode.txt" && "$isWriteChanges" == true ]]; then
  echo "'$changelogsPath/$tagVersionCode.txt' already exists, deleting..."
  rm "$changelogsPath/$tagVersionCode.txt"
fi

echo "Generating Changelog between $tag and $referenceTag..."
for commitHash in $commitHashesBetweenTags
do
  subject=$(git log --format=%s -n 1 $commitHash | sed -e 's/Change-Id:\s*.*//' | sed -e 's/Signed-off-by:\s*.*//' | sed -e 's/^[^a-zA-Z0-9]*//')
  body=$(git log --format=%b -n 1 $commitHash | sed -e 's/Change-Id:\s*.*//' | sed -e 's/Signed-off-by:\s*.*//' | sed -e 's/^[^a-zA-Z0-9]*//')

  subjects+=("$subject")
  bodies+=("$body")
  changelogs=${#subjects[@]}

  if [[ $changelogs -eq 1 && "$isWriteChanges" == true ]]; then
    echo "saving to '$changelogsPath/$tagVersionCode.txt'..."
  fi

  echo "Commit: $commitHash"
  echo "* $subject"

  if [ "$isWriteChanges" == true ]; then
    echo "* $subject" >> "$changelogsPath/$tagVersionCode.txt"
  fi

  readarray -t lines <<< "$body"
  lineCount=${#lines[@]}
  for line in "${lines[@]}"; do
    if [[ -n "$line" ]]; then
      if [ $lineCount -gt 1 ]; then
        echo "   > - $line"

        if [ "$isWriteChanges" == true ]; then
          echo "   > - $line" >> "$changelogsPath/$tagVersionCode.txt"
        fi
      else
        echo "   > $line"

        if [ "$isWriteChanges" == true ]; then
          echo "   > $line" >> "$changelogsPath/$tagVersionCode.txt"
        fi
      fi
    else
      if [ $lineCount -eq 1 ]; then
        echo "   >"

        if [ "$isWriteChanges" == true ]; then
          echo "   >" >> "$changelogsPath/$tagVersionCode.txt"
        fi
      fi
    fi
  done
  echo "------------------------------"
done

if [ $changelogs -gt 0 ]; then
  if [ "$isWriteChanges" == true ]; then
    echo "$changelogs changelog(s) saved to '$changelogsPath/$tagVersionCode.txt'!"
    echo >> "$changelogsPath/$tagVersionCode.txt"
  else
    echo "$changelogs changelog(s) found"
  fi

  echo

  echo "**Full Changelog**: https://github.com/abdalmoniem/Caffeinate/compare/$referenceTag...$tag"

  if [ "$isWriteChanges" == true ]; then
    echo "**Full Changelog**: https://github.com/abdalmoniem/Caffeinate/compare/$referenceTag...$tag" \
    >> "$changelogsPath/$tagVersionCode.txt"
  fi


  if [ "$isCommitChanges" == true ]; then
    currentCommitHash=$(git rev-parse HEAD)
    isCurrentCommitOnRemote=$(git branch -r --contains "$currentCommitHash")
    if [ -n "$isCurrentCommitOnRemote" ]; then
      echo "commit '$currentCommitHash' is on the remote branch, creating a new change log commit..."
      echo

      git add "$changelogsPath/"
      git commit -sm "updated $changelogs change log(s)"
    else
      echo "commit '$currentCommitHash' is not on the remote branch, amending..."
      echo

      git add "$changelogsPath/"
      git commit --amend --no-edit
    fi

    oldTagRef=$(git rev-parse "$tag")
    newTagRef=$(git rev-parse "HEAD")

    echo
    echo "changing tag: $tag reference from $oldTagRef to $newTagRef..."
    git tag -d "$tag" && git push origin :refs/tags/"$tag"

    echo "adding tag: $tag..."
    git tag "$tag"
    echo "tag $tag added!"
  fi


else
  echo "No / $changelogs change log(s) found between $referenceTag and $tag"
fi