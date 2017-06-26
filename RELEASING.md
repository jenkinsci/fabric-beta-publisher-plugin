# Releasing

Releasing is done by running the following command with your jenkins-ci.org account:
```
$ mvn release:prepare release:perform -Dusername=... -Dpassword=...
```

If your release has failed and you want to redo it, then you have to:
1. Reset `master` to the commit before `maven release` kicked in:
   ```
   $ git reset --hard SHA1
   ```
2. Delete the tag Maven created:
   ```
   $ git tag -d fabric-beta-publisher-{version}
   ```
3. Push these changes to the repository:
   ```
   $ git push -f origin master 
   $ git push origin :fabric-beta-publisher-{version}
   ```

## Important

**Do not split** that Maven command into two separate Maven invocations (i.e. `mvn release:prepare` followed by `mvn release:perform`). It won't work as you expect and will mess up your release. Always execute the two Maven goals together, in one command.


---

See [official Jenkins](https://wiki.jenkins.io/display/JENKINS/Hosting+Plugins#HostingPlugins-Releasingtojenkins-ci.org) docs for more information.
