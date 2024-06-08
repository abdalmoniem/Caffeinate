#!/bin/bash

# Replace with your repository owner
OWNER="abdalmoniem"
# Replace with your repository name
REPO="Caffeinate"

# List all published releases and store their tags in a temporary variable
published_tags=$(gh release list --repo "$OWNER/$REPO" --json tagName,isDraft | jq -r '.[] | select(.isDraft == false) | .tagName')

# Reverse the order of release tags
reversed_tags=$(echo "$published_tags" | tac)

# Iterate over each published release tag
for release in $reversed_tags
do
  release=$(echo "$release" | xargs)

  # Update the release to be a draft again
  gh release edit "$release" --repo "$OWNER/$REPO" --draft=true

  echo "Redrafted release: $release"
  echo

  # Introduce a delay of 1 second to avoid rate limiting (optional)
  # sleep 1
done

echo
echo "All published releases redrafted."
