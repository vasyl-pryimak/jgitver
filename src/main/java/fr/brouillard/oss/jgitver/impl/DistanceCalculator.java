/**
 * Copyright (C) 2016 Matthieu Brouillard [http://oss.brouillard.fr/jgitver] (matthieu@brouillard.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.brouillard.oss.jgitver.impl;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;

import java.util.Queue;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Allow to compute git depth, in term of commit distance between several commits.
 */
public interface DistanceCalculator {
    /**
     * Creates a reusable {@link DistanceCalculator} on the given repository for the given start commit.
     * @param start the commit where the computation will start
     * @param repository the repository on which to operate
     * @param maxDepth the maximum depth to which we accept to look at. If <= 0 then Integer.MAX_VALUE will be used.
     * @return a reusable {@link DistanceCalculator} object
     */
    static DistanceCalculator create(ObjectId start, Repository repository, int maxDepth) {
        return new DepthWalkDistanceCalculator(start, repository, maxDepth > 0 ? maxDepth : Integer.MAX_VALUE);
    }

    /**
     * Creates a reusable {@link DistanceCalculator} on the given repository for the given start commit,
     * uses Integer.MAX_VALUE as the maximum depth distance.
     * @see #create(ObjectId, Repository, int)
     */
    static DistanceCalculator create(ObjectId start, Repository repository) {
        return create(start, repository, Integer.MAX_VALUE);
    }

    /**
     * Computes an eventual distance between the start commit given at build time and the provided target commit.
     * Returns the computed distance inside an Optional which can be empty if the given target is not reachable
     * or is too far regarding the given distance.
     * @param target the commit to compute distance for
     * @return a distance as an Optional
     */
    Optional<Integer> distanceTo(ObjectId target);

    class DepthWalkDistanceCalculator implements DistanceCalculator {
        private final ObjectId startId;
        private final Repository repository;
        private final int maxDepth;

        DepthWalkDistanceCalculator(ObjectId start, Repository repository, int maxDepth) {
            this.startId = start;
            this.repository = repository;
            this.maxDepth = maxDepth;
        }

      /**
       * Calculates the distance (number of commits) between the given parent and child commits.
       *
       * @return distance (number of commits) between the given commits
       * @see <a href="https://github.com/mdonoughe/jgit-describe/blob/master/src/org/mdonoughe/JGitDescribeTask.java">mdonoughe/jgit-describe/blob/master/src/org/mdonoughe/JGitDescribeTask.java</a>
       */
        public Optional<Integer> distanceTo(ObjectId target) {
            try (final RevWalk revWalk = new RevWalk(repository)) {
                revWalk.markStart(revWalk.parseCommit(startId));

                Set<ObjectId> seena = new HashSet<>();
                Set<ObjectId> seenb = new HashSet<>();
                Queue<RevCommit> q = new ArrayDeque<>();

                q.add(revWalk.parseCommit(startId));
                int distance = 0;

                while (q.size() > 0) {
                    RevCommit commit = q.remove();
                    ObjectId commitId = commit.getId();

                    if (seena.contains(commitId)) {
                        continue;
                    }
                    seena.add(commitId);

                    if (target.equals(commitId)) {
                        // don't consider commits that are included in this commit
                        seeAllParents(revWalk, commit, seenb);
                        // remove things we shouldn't have included
                        for (ObjectId oid : seenb) {
                            if (seena.contains(oid)) {
                                distance--;
                            }
                        }
                        seena.addAll(seenb);
                        continue;
                    }

                    for (ObjectId oid : commit.getParents()) {
                        if (!seena.contains(oid)) {
                            q.add(revWalk.parseCommit(oid));
                        }
                    }
                    distance++;
                }
                return Optional.of(distance);
            } catch (Exception e) {
                throw new RuntimeException(String.format("Unable to calculate distance between [%s] and [%s]", startId, target), e);
            }
        }

        private void seeAllParents(RevWalk revWalk, RevCommit child, Set<ObjectId> seen) throws IOException {
            Queue<RevCommit> q = new ArrayDeque<>();
            q.add(child);

            while (q.size() > 0) {
                RevCommit commit = q.remove();
                for (ObjectId oid : commit.getParents()) {
                    if (seen.contains(oid)) {
                        continue;
                    }
                    seen.add(oid);
                    q.add(revWalk.parseCommit(oid));
                }
            }
        }
    }
}
