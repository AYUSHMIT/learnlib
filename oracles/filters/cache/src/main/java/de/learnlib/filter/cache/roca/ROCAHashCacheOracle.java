/* Copyright (C) 2021 – University of Mons, University Antwerpen
 * This file is part of LearnLib, http://www.learnlib.de/..
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.learnlib.filter.cache.roca;

import de.learnlib.api.oracle.MembershipOracle;

/**
 * A cache oracle for membership queries over an ROCA.
 * 
 * @param <I> Input symbol type
 * @author Gaëtan Staquet
 */
public class ROCAHashCacheOracle<I> extends AbstractHashCacheOracle<I, Boolean>
        implements MembershipOracle.ROCAMembershipOracle<I> {
    public ROCAHashCacheOracle(MembershipOracle.ROCAMembershipOracle<I> delegate) {
        super(delegate);
    }

}
