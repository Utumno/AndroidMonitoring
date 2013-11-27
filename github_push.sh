git checkout master@{0}
git reset --soft github
git commit
git branch temp
git checkout temp
git branch -M github
git push -v  origin github:master
git checkout master
