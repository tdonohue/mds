/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.event;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.dspace.core.Context;
import org.dspace.core.Utils;

/**
 * BasicDispatcher implements the primary task of a Dispatcher: it delivers a
 * filtered list of events, synchronously, to a configured list of consumers. It
 * may be extended for more elaborate behavior.
 * 
 * @version $Revision: 5844 $
 */
public class BasicDispatcher extends Dispatcher
{

    public BasicDispatcher(String name)
    {
        super(name);
    }

    /** log4j category */
    private static Logger log = LoggerFactory.getLogger(BasicDispatcher.class);

    public void addConsumerProfile(ConsumerProfile cp)
            throws IllegalArgumentException
    {
        if (consumers.containsKey(cp.getName()))
        {
            throw new IllegalArgumentException(
                    "This dispatcher already has a consumer named \""
                            + cp.getName() + "\"");
        }

        consumers.put(cp.getName(), cp);

        if (log.isDebugEnabled())
        {
            int n = 0;
            for (Iterator i = cp.getFilters().iterator(); i.hasNext(); ++n)
            {
                int f[] = (int[]) i.next();
                log.debug("Adding Consumer=\"" + cp.getName() + "\", instance="
                        + cp.getConsumer().toString() + ", filter["
                        + String.valueOf(n) + "]=(ObjMask="
                        + String.valueOf(f[Event.SUBJECT_MASK])
                        + ", EventMask=" + String.valueOf(f[Event.EVENT_MASK])
                        + ")");
            }
        }
    }

    /**
     * Dispatch all events added to this Context according to configured
     * consumers.
     * 
     * @param ctx
     *            the execution context
     */
    public void dispatch(Context ctx)
    {
        if (!consumers.isEmpty())
        {
            List<Event> events = Collections.synchronizedList(ctx.getEvents());

            if (events == null)
            {
                return;
            }

            if (log.isDebugEnabled())
            {
                log.debug("Processing queue of "
                        + String.valueOf(events.size()) + " events.");
            }

            // transaction identifier applies to all events created in
            // this context for the current transaction. Prefix it with
            // some letters so RDF readers don't mistake it for an integer.
            String tid = "TX" + Utils.generateKey();

            for (Event event : events)
            {
                event.setDispatcher(getIdentifier());
                event.setTransactionID(tid);

                if (log.isDebugEnabled())
                {
                    log.debug("Iterating over "
                            + String.valueOf(consumers.values().size())
                            + " consumers...");
                }

                for (Iterator ci = consumers.values().iterator(); ci.hasNext();)
                {
                    ConsumerProfile cp = (ConsumerProfile) ci.next();

                    if (event.pass(cp.getFilters()))
                    {
                        if (log.isDebugEnabled())
                        {
                            log.debug("Sending event to \"" + cp.getName()
                                    + "\": " + event.toString());
                        }

                        try
                        {
                            cp.getConsumer().consume(ctx, event);

                            // Record that the event has been consumed by this
                            // consumer
                            event.setBitSet(cp.getName());
                        }
                        catch (Exception e)
                        {
                            log.error("Consumer(\"" + cp.getName()
                                    + "\").consume threw: " + e.toString(), e);
                        }
                    }

                }
            }

            // Call end on the consumers that got synchronous events.
            for (Iterator ci = consumers.values().iterator(); ci.hasNext();)
            {
                ConsumerProfile cp = (ConsumerProfile) ci.next();
                if (cp != null)
                {
                    if (log.isDebugEnabled())
                    {
                        log.debug("Calling end for consumer \"" + cp.getName()
                                + "\"");
                    }

                    try
                    {
                        cp.getConsumer().end(ctx);
                    }
                    catch (Exception e)
                    {
                        log.error("Error in Consumer(\"" + cp.getName()
                                + "\").end: " + e.toString(), e);
                    }
                }
            }
        }
    }

}
