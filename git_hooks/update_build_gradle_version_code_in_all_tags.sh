startingVersion=1

HEAD=$(git rev-parse HEAD)
currentBranch=$(git rev-parse --abbrev-ref HEAD)
tags=($(git tag))

filename=./app/build.gradle.kts
filter="\(versionCode\s\+=\s\+\)\([[:digit:]]\+\)"

sed -i "s/$filter/\1$startingVersion/" $filename
git add $filename
git commit --amend --no-edit --quiet # || continue

echo "HEAD is at $currentBranch -> $HEAD"

# Iterate over all tags
for tag in ${tags[@]}
do
  echo "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
  echo "checking out tag: $tag ..."
  # Check out the tag
  git checkout --quiet $tag

  echo "deleting tag $tag ..."
  git tag -d $tag

  # # Replace the line in the file
  sed -i "s/$filter/\1$startingVersion/" $filename
  # cat $filename | sed -e "s/$filter/\1$startingVersion/" | grep -e $filter | tr -s ' \n'

  ((startingVersion+=1))

#  echo "deleting tag $tag from origin..."
#  git push origin :$tag # || continue

  # Commit the changes
  echo "adding $filename to git staging..."
  git add $filename

  echo "committing change: $(cat $filename | grep -e $filter | tr -s ' \n') ..."
#  git commit --amend --no-edit --quiet # || continue
  git commit -sm "updated tag $tag" -m "updated $filename::versionCode=$startingVersion in tag $tag"

  echo "adding tag $tag ..."
  git rtag $tag

  echo "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
  echo
done

echo "switching back to $currentBranch -> $HEAD..."
git switch --quiet $currentBranch
