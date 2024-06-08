#!/bin/bash

# Replace with your repository owner
OWNER="abdalmoniem"
# Replace with your repository name
REPO="Caffeinate"

# List all draft releases and store their tags in a temporary variable
draft_tags=$(gh release list --repo "$OWNER/$REPO" --json tagName,isDraft | jq -r '.[] | select(.isDraft == true) | .tagName')

# Reverse the order of release tags
reversed_tags=$(echo "$draft_tags" | tac)

# Iterate over each draft release tag
for release in $reversed_tags
do
  release=$(echo "$release" | xargs)

  # echo "gh release edit "$release" --repo "$OWNER/$REPO" --draft=false"

  # Publish the draft release
  gh release edit "$release" --repo "$OWNER/$REPO" --draft=false --latest=true

  echo "Published release: $release"
  echo

  # Introduce a delay of 1 second to avoid rate limiting (optional)
  # sleep 1
done

echo
echo "All draft releases published."
