/*
 * Copyright 2015 Midokura SARL
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

package org.midonet.midolman

import java.lang.reflect.Type

import org.midonet.cluster.data.ZoomConvert
import org.midonet.cluster.models.Topology.Rule.{NatTarget => ProtoNatTarget}
import org.midonet.cluster.util.IPAddressUtil
import org.midonet.midolman.rules.NatTarget

object NatTargetUtil {
    sealed class Converter extends ZoomConvert.Converter[NatTarget,
                                                         ProtoNatTarget] {

        override def toProto(value: NatTarget, clazz: Type): ProtoNatTarget =
            ProtoNatTarget.newBuilder
                .setNwStart(IPAddressUtil.toProto(value.nwStart))
                .setNwEnd(IPAddressUtil.toProto(value.nwEnd))
                .setTpStart(value.tpStart)
                .setTpEnd(value.tpEnd)
                .build()

        override def fromProto(value: ProtoNatTarget, clazz: Type): NatTarget = {
            new NatTarget(IPAddressUtil.toIPv4Addr(value.getNwStart),
                          IPAddressUtil.toIPv4Addr(value.getNwEnd),
                          value.getTpStart, value.getTpEnd)
        }
    }
}
