/*
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
module org.trellisldp.event.jackson {
    exports org.trellisldp.event.jackson;

    requires transitive org.trellisldp.api;
    requires transitive org.trellisldp.vocabulary;

    requires org.apache.commons.rdf.api;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.fasterxml.jackson.annotation;
    requires cdi.api;

    opens org.trellisldp.event.jackson to com.fasterxml.jackson.databind;

    provides org.trellisldp.api.EventSerializationService
        with org.trellisldp.event.jackson.DefaultEventSerializationService;
}
