# Arrays to hold commit hashes and tag names
declare -a remote_commit_hashes
declare -a remote_tag_names

# Parse the output of git ls-remote --tags
while IFS= read -r line; do
    commit_hash=$(echo "$line" | awk '{print $1}')
    tag_name=$(echo "$line" | awk '{print $2}' | sed 's|refs/tags/||')

    remote_commit_hashes+=("$commit_hash")
    remote_tag_names+=("$tag_name")
done < <(git ls-remote --tags)

for i in "${!remote_tag_names[@]}"; do
   echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"

   remote_commit_hash=${remote_commit_hashes[$i]}
   remote_commit_tag=${remote_tag_names[$i]}

   echo "tag: $remote_commit_tag"

   remote_tag_commit_subject=$(git show -s --format=%s $remote_commit_hash)

   echo "remote commit subject: $remote_tag_commit_subject"
   echo "remote commit hash: $remote_commit_hash"

   local_hash=$(git log main --oneline --no-abbrev-commit --grep="^$remote_tag_commit_subject$" | grep -oE "^\w+\s+")

   if [ -n "$local_hash" ]; then
      echo "local commit hash: $local_hash"

      echo "deleting local tag: $remote_commit_tag..."
      git tag -d $remote_commit_tag

      echo "adding local tag to hash $local_hash..."
      git tag $remote_commit_tag $local_hash

      echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
      echo
   fi
done
