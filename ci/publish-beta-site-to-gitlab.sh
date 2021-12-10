#!/bin/sh

REPO_URL=gitlab.com/utybo/epilink-beta-pages-deployment
REPO="https://oauth2:${GITLAB_SITE_PUSH_TOKEN}@${REPO_URL}"

set -e

echo "Publishing website to $REPO_URL"
./gradlew :docs:build
mkdir /tmp/publish-site-to-gitlab
cp -r docs/build/docs /tmp/publish-site-to-gitlab/public
cat > /tmp/publish-site-to-gitlab/.gitlab-ci.yml <<EOF
image: alpine:latest
pages:
  stage: deploy
  script:
  - echo 'Nothing to do...'
  artifacts:
    paths:
    - public
  only:
  - master
EOF

cd /tmp/publish-site-to-gitlab
git init
git config user.name "Automatic push"
git config user.email "no-email@example.org"
git remote add origin "$REPO"
git add .
git commit -m "Publish"
git push -u origin master --force

echo "Done"
echo "WARNING: Please rm /tmp/publish-site-to-gitlab manually if you wish to run this script again."
