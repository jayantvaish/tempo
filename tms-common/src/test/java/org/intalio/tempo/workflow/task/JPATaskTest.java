package org.intalio.tempo.workflow.task;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import javax.persistence.Query;

import junit.framework.Assert;
import junit.framework.JUnit4TestAdapter;

import org.intalio.tempo.workflow.auth.UserRoles;
import org.intalio.tempo.workflow.task.attachments.Attachment;
import org.intalio.tempo.workflow.task.attachments.AttachmentMetadata;
import org.intalio.tempo.workflow.task.xml.TaskMarshaller;
import org.intalio.tempo.workflow.task.xml.XmlTooling;
import org.intalio.tempo.workflow.util.TaskEquality;
import org.intalio.tempo.workflow.util.jpa.TaskFetcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class JPATaskTest {
	final static Logger _logger = LoggerFactory.getLogger(JPATaskTest.class);

	EntityManager em;
	EntityManagerFactory factory;
	EntityTransaction jpa;
	XmlTooling xml = new XmlTooling();
	int uniqueID = 0;

	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(JPATaskTest.class);
	}

	private synchronized String getUniqueTaskID() {
		uniqueID++;
		return "id_" + uniqueID;
	}

	@Before
	public void setUpEntityManager() throws Exception {
		Properties p = new Properties();
		p.load(this.getClass().getResourceAsStream("/jpa.properties"));
		System.getProperties().putAll(p);
		factory = Persistence.createEntityManagerFactory(
				"org.intalio.tempo.tms", System.getProperties());
		em = factory.createEntityManager();
		jpa = em.getTransaction();
	}

	@After
	public void closeFactory() throws Exception {
		try {
			em.close();
		} catch (Exception e) {
		}
		try {
			factory.close();
		} catch (Exception e) {
		}
	}

	private void persist(Object o) throws Exception {
		jpa.begin();
		em.persist(o);
		jpa.commit();
		// this is to prevent caching at the entity manager level
		em.clear();
	}

	private void testRange(int first, int max, int expected) {
		TaskFetcher taskFetcher = new TaskFetcher(factory.createEntityManager());
		HashMap map = new HashMap();
		map.put(TaskFetcher.FETCH_USER, new UserRoles("user1",
				new String[] { "examples\\employee" }));
		map.put(TaskFetcher.FETCH_CLASS, PATask.class);
		map.put(TaskFetcher.FETCH_SUB_QUERY, "");
		map.put(TaskFetcher.FETCH_FIRST, first);
		map.put(TaskFetcher.FETCH_MAX, max);
		this._logger.debug("Testing range with :first=" + first + " and :max="
				+ max + " ; expected:" + expected);
		Assert.assertEquals(expected,
				taskFetcher.fetchAvailableTasks(map).length);
	}

	private void testCount(long expected) throws Exception {
		final TaskFetcher taskFetcher = new TaskFetcher(em);
		HashMap map = new HashMap();
		map.put(TaskFetcher.FETCH_USER, new UserRoles("user1",
				new String[] { "examples\\employee" }));
		map.put(TaskFetcher.FETCH_CLASS, PATask.class);
		Assert
				.assertEquals((Long) expected, (Long) taskFetcher
						.countTasks(map));

		remoteQuery();
	}

	private void testLogins(String user, String[] users)
			throws URISyntaxException, Exception {
		String id = getUniqueTaskID();
		Notification task1 = null, task2 = null;
		task1 = new Notification(id, new URI("http://hellonico.net"),
				getXmlSampleDocument());
		task1.getUserOwners().add(user);
		persist(task1);

		for (String us : users) {
			_logger.info(user + ":" + us + ":");
			UserRoles ur = new UserRoles(us, new String[] {});
			Task[] tasks = new TaskFetcher(em).fetchAllAvailableTasks(ur);
			if (tasks.length > 0)
				Assert.assertEquals(tasks[0].isAvailableTo(ur), true);
		}

		checkRemoved(task1);
	}

	private Task[] testFetchForUserRolesWithCriteria(String userId,
			String[] roles, Class taskClass, String subQuery, int size)
			throws Exception {
		Task[] tasks = new TaskFetcher(em).fetchAvailableTasks(new UserRoles(
				userId, roles), taskClass, subQuery);
		Assert.assertEquals(size, tasks.length);
		return tasks;
	}

	private Task[] testFetchForUserRolesWithCriteria(String userId,
			String[] roles, Class taskClass, String subQuery, int size,
			boolean optionalMarshalling) throws Exception {
		Task[] tasks = new TaskFetcher(em).fetchAvailableTasks(new UserRoles(
				userId, roles), taskClass, subQuery);
		Assert.assertEquals(size, tasks.length);

		if (optionalMarshalling) {
			TaskMarshaller marshaller = new TaskMarshaller();
			for (Task task : tasks)
				marshaller.marshalFullTask(task, null);
		}

		return tasks;
	}

	private Task[] testFecthForUserRoles(String userId, String[] roles, int size)
			throws Exception {
		Task[] tasks = new TaskFetcher(em)
				.fetchAllAvailableTasks(new UserRoles(userId, roles));
		Assert.assertEquals(size, tasks.length);
		return tasks;
	}

	private Document getXmlSampleDocument() throws Exception {
		return xml.getXmlDocument("/inputWithNamespace.xml");
	}

	private void checkRemoved(Task task2) {
		checkRemoved(task2.getID());
	}

	private void checkRemoved(String id) {
		final TaskFetcher taskFetcher = new TaskFetcher(em);
		jpa.begin();
		taskFetcher.deleteTasksWithID(id);
		jpa.commit();
		try {
			taskFetcher.fetchTaskIfExists(id);
			Assert.fail("No task should be left here");
		} catch (Exception expected) {
			// this is good.
		}
	}

	private void checkRemoved(Task[] tasks) {
		for (Task t : tasks)
			checkRemoved(t);
	}

	private void checkRemoved(List<Task> tasks) {
		for (Task t : tasks)
			checkRemoved(t);
	}

	private PATask getSampleTask(TaskState state) throws URISyntaxException,
			Exception {
		String id = getUniqueTaskID();
		PATask task1 = new PATask(id, new URI("http://hellonico.net"),
				"processId", "soap", getXmlSampleDocument());
		task1.authorizeActionForUser("save", "examples\\manager");
		task1.setPriority(2);
		task1.setState(state);
		task1.getRoleOwners().add("role1");
		task1.getUserOwners().add("user1");
		return task1;
	}

	private void remoteQuery() throws Exception {
		Query q = em
				.createQuery("select COUNT(T) from PIPATask T where (T._description like '%hello%')");
		q.getSingleResult();
	}

	private PATask[] getSampleTasks(int max) throws Exception {
		PATask[] tasks = new PATask[max];
		for (int i = 0; i < max; i++)
			tasks[i] = getSampleTask(TaskState.READY);
		return tasks;
	}

     @Test
          public void testNativeQueryForAllPATasks() throws Exception {
          
           UserRoles ur = new UserRoles("niko",
                   new String[] { "examples\\employee" });
           final TaskFetcher taskFetcher = new TaskFetcher(em);
          
           // get your query from here
           Task[] list1 = taskFetcher.fetchAvailableTasks(ur, PATask.class, "");
           // we make sure no previous tasks left here
           checkRemoved(list1);
          
           // create and persist a new task for user niko
           PATask task1 = new PATask(getUniqueTaskID(), new URI(
                   "http://hellonico.net"));
           task1.getUserOwners().add("niko");
           task1.getRoleOwners().add("examples\\employee");
           persist(task1);
          
           // create and persist a new task for user niko2
           PATask task2 = new PATask(getUniqueTaskID(), new URI(
                   "http://hellonico.net"));
           task2.getUserOwners().add("niko2");
           task2.getRoleOwners().add("examples\\employee");
           persist(task2);
          
           String query = "SELECT DISTINCT t1.id, t0.ID, t1.creation_date, t1.description, t1.form_url, t1.taskid, t1.internal_id, t0.complete_soap_action, t0.deadline, t0.instance_id, t0.is_chained_before, t0.previous_task_id, t0.priority, t0.process_id, t0.state FROM tempo_pa t0 INNER JOIN tempo_task t1 ON t0.ID = t1.id LEFT OUTER JOIN tempo_user t2 ON t1.id = t2.TASK_ID LEFT OUTER JOIN tempo_role t3 ON t1.id = t3.TASK_ID WHERE (t2.element IN (?) OR t3.element IN (?))";
           // see: http://blog.randompage.org/2006/06/jpa-native-queries.html
           Query q = em.createNativeQuery(query, PATask.class);
           q.setParameter(1, ""); // negate the user
           q.setParameter(2, "examples\\employee"); // use that role
          
           // start the native query
           List<Task> list = q.getResultList();
           for (Task t : list)
               System.out.println(t.toString());
           Assert.assertEquals(2, list.size());
           checkRemoved(list);
          }
          
          @Test
          public void testInjection() throws Exception {
           PATask task1 = new PATask(getUniqueTaskID(), new URI(
                   "http://hellonico.net"));
           task1.setInput("OR true; DELETE * FROM tempo_task;");
           task1.getUserOwners().add("niko");
           persist(task1);
           UserRoles ur = new UserRoles("niko",
                   new String[] { "examples\\employee" });
           final TaskFetcher taskFetcher = new TaskFetcher(em);
           Task[] t1 = taskFetcher.fetchAvailableTasks(ur, PATask.class, "");
          
           Assert.assertEquals(1, t1.length);
           checkRemoved(new Task[] { task1 });
          }
          
         @Test
         public void testAttachments() throws Exception {
          PATask task1 = new PATask(getUniqueTaskID(), new URI(
                  "http://hellonico.net"));
          AttachmentMetadata attMetadata = new AttachmentMetadata();
          Attachment att = new Attachment(attMetadata, new URL(
                  "file:///thisisadocument1.txt"));
          Attachment att2 = new Attachment(attMetadata, new URL(
                  "file:///thisisadocument2.txt"));
          task1.addAttachment(att);
          task1.addAttachment(att2);
          task1.getUserOwners().add("niko");
          persist(task1);
         
          PATask task2 = new PATask(getUniqueTaskID(), new URI(
                  "http://hellonico.net"));
          task2.getUserOwners().add("niko");
          AttachmentMetadata attMetadata2 = new AttachmentMetadata();
          Attachment att3 = new Attachment(attMetadata2, new URL(
                  "file:///thisisadocument3.txt"));
          task2.addAttachment(att3);
          persist(task2);
         
          UserRoles ur = new UserRoles("niko",
                  new String[] { "examples\\employee" });
          final TaskFetcher taskFetcher = new TaskFetcher(em);
         
          // test task1 has two attachments
          String query = "(T._id = '" + task1.getID() + "')";
          Task[] t2 = taskFetcher.fetchAvailableTasks(ur, PATask.class, query);
          Assert.assertTrue(t2[0] instanceof PATask);
          PATask t = (PATask) t2[0];
          Collection<Attachment> atts = t.getAttachments();
          Assert.assertEquals(2, atts.size());
         
          // test the two attachments are different
          Iterator<Attachment> iter = atts.iterator();
          Attachment a1 = iter.next();
          Attachment a2 = iter.next();
          Assert.assertNotSame(a1.getPayloadURL(), a2.getPayloadURL());
         
          // test task2 has one attachment
          query = "(T._id = '" + task2.getID() + "')";
          t2 = taskFetcher.fetchAvailableTasks(ur, PATask.class, query);
          Assert.assertTrue(t2[0] instanceof PATask);
          t = (PATask) t2[0];
          Assert.assertEquals(1, t.getAttachments().size());
         
          // task2 attachments are not the same as task1
          URL ur1 = t.getAttachments().iterator().next().getPayloadURL();
          Assert.assertNotSame(a1.getPayloadURL(), ur1);
          Assert.assertNotSame(a2.getPayloadURL(), ur1);
         
          checkRemoved(new Task[] { task1, task2 });
         }
         
         @Test
            public void testUTF() throws Exception {
             PATask task1 = new PATask(getUniqueTaskID(), new URI(
                     "http://hellonico.net"));
             String someJapanese = "\u201e\u00c5\u00c7\u201e\u00c7\u00e4\u201e\u00c5\u00e5\u201e\u00c5\u00ae\u201e\u00c5\u00dc";
             task1.setInput(someJapanese);
             task1.getUserOwners().add(someJapanese);
             persist(task1);
             UserRoles ur = new UserRoles(someJapanese,
                     new String[] { "examples\\employee" });
             final TaskFetcher taskFetcher = new TaskFetcher(em);
             Task[] t1 = taskFetcher.fetchAvailableTasks(ur, PATask.class, "");
             Assert.assertEquals(1, t1.length);
             checkRemoved(new Task[] { task1 });
            }
    
      @Test
      public void testFetchRangeAndCount() throws Exception {
       PATask[] tasks = getSampleTasks(10);
       for (Task t : tasks)
           persist(t);

       testRange(1, 2, 2);
       testRange(1, 1, 1);
       testRange(-1, 1, 1);
       testRange(1, 1, 1);
       testRange(1, 5, 5);
       testCount(tasks.length);
       checkRemoved(tasks);
      }
             
           @Test
           public void testMultiplePipa() throws Exception {
            PIPATask task1 = new PIPATask(getUniqueTaskID(), new URI(
                    "http://hellonico2.net"),
                    new URI("http://hellonicoUnique1.net"), new URI(
                            "http://hellonico2.net"), "initOperationSOAPAction");
            task1.getUserOwners().add("niko");
            persist(task1);
            PIPATask task2 = new PIPATask(getUniqueTaskID(), new URI(
                    "http://hellonico2.net"),
                    new URI("http://hellonicoUnique2.net"), new URI(
                            "http://hellonico2.net"), "initOperationSOAPAction");
            task2.getUserOwners().add("niko");
            persist(task2);
           
            UserRoles ur = new UserRoles("niko",
                    new String[] { "examples\\employee" });
            final TaskFetcher taskFetcher = new TaskFetcher(em);
            Task[] ts = taskFetcher.fetchAvailableTasks(ur, PIPATask.class, "");
            Assert.assertEquals(2, ts.length);
           
            PIPATask t3 = taskFetcher
                    .fetchPipaFromUrl("http://hellonicoUnique1.net");
            TaskEquality.areTasksEquals(task1, t3);
           
            PIPATask tt1 = taskFetcher.fetchPipaFromUrl(task1.getProcessEndpoint()
                    .toString());
            TaskEquality.areTasksEquals(task1, tt1);
           
            jpa.begin();
            em.remove(tt1);
            jpa.commit();
           
            try {
                PIPATask ttt1 = taskFetcher.fetchPipaFromUrl(task1
                        .getProcessEndpoint().toString());
                Assert.fail("Should throw an exception");
            } catch (Exception e) {
           
            }
           
            checkRemoved(new Task[] { task1, task2 });
           }
           
           @Test
           public void oneMoreTestForDescriptionSearch() throws Exception {
            String query = "(T._state = TaskState.READY OR T._state = TaskState.CLAIMED) AND T._description like '%hello%' ORDER BY T._creationDate DESC";
            PATask task1 = new PATask(getUniqueTaskID(), new URI(
                    "http://hellonico.net"));
            task1.setDescription("bonjour");
            task1.getUserOwners().add("niko");
            persist(task1);
           
            PATask task2 = new PATask(getUniqueTaskID(), new URI(
                    "http://hellonico.net"));
            task2.setDescription("hello");
            task2.getUserOwners().add("niko");
            persist(task2);
           
            UserRoles ur = new UserRoles("niko",
                    new String[] { "examples\\employee" });
            final TaskFetcher taskFetcher = new TaskFetcher(em);
            Task[] t1 = taskFetcher.fetchAvailableTasks(ur, PATask.class, query);
            Assert.assertEquals(1, t1.length);
           
            checkRemoved(new Task[] { task1, task2 });
           }
           
           @Test
           public void plentyOfRoles() throws Exception {
            PATask task1 = new PATask(getUniqueTaskID(), new URI(
                    "http://hellonico.net"));
            ArrayList<String> list = new ArrayList<String>();
            for (int i = 0; i < 200; i++)
                list.add(new String("proto\\PARIS_MEDI_Liquidateur_" + i));
            list.add(new String("proto\\RoleNotFound"));
            String[] assignedRoles = list.toArray(new String[list.size()]);
            for (String role : assignedRoles)
                task1.getRoleOwners().add(role);
            task1.getUserOwners().add("niko");
            persist(task1);
           
            UserRoles ur = new UserRoles("niko", assignedRoles);
            final TaskFetcher taskFetcher = new TaskFetcher(em);
            Task[] t1 = taskFetcher.fetchAvailableTasks(ur, PATask.class, "");
           
            Assert.assertEquals(1, t1.length);
            checkRemoved(new Task[] { task1 });
           }
           
           @Test
           public void avoidClaimComingBack() throws Exception {
            PATask task1 = new PATask(getUniqueTaskID(), new URI(
                    "http://hellonico.net"));
            task1.setState(TaskState.CLAIMED);
            task1.getRoleOwners().add("examples\\employee");
            task1.getUserOwners().add("niko");
            PATask task2 = new PATask(getUniqueTaskID(), new URI(
                    "http://hellonico.net"));
            task2.setState(TaskState.FAILED);
            task2.getRoleOwners().add("examples\\employee");
            task2.getUserOwners().add("niko");
            PATask task3 = new PATask(getUniqueTaskID(), new URI(
                    "http://hellonico.net"));
            task3.setState(TaskState.READY);
            task3.getRoleOwners().add("examples\\employee");
            task3.getUserOwners().add("niko");
            persist(task1);
            persist(task2);
            persist(task3);
           
            final TaskFetcher taskFetcher = new TaskFetcher(em);
            UserRoles ur = new UserRoles("niko",
                    new String[] { "examples\\employee" });
            Task[] t1 = taskFetcher.fetchAvailableTasks(ur, PATask.class, "");
            Task[] t2 = taskFetcher.fetchAvailableTasks(ur, PATask.class,
                    "T._state = TaskState.READY OR T._state = TaskState.CLAIMED");
            UserRoles ur2 = new UserRoles("alex",
                    new String[] { "examples\\employee2" });
            Task[] t3 = taskFetcher.fetchAvailableTasks(ur2, PATask.class,
                    "T._state = TaskState.READY OR T._state = TaskState.CLAIMED");
           
            Assert.assertEquals(3, t1.length);
            Assert.assertEquals(2, t2.length);
            Assert.assertEquals(0, t3.length);
            checkRemoved(new Task[] { task1, task2, task3 });
           }
           
           @Test
           public void faultyQueryWithOpenJPA1_2() throws Exception {
           
            String user = "niko";
            List<String> params = new ArrayList<String>();
            params.add(user);
           
            Task task1 = new Notification(getUniqueTaskID(), new URI(
                    "http://hellonico.net"), getXmlSampleDocument());
            task1.getUserOwners().add(user);
            persist(task1);
            em.clear(); // <--- this was causing problems as getSingleResult is
            // optimized in openjpa12 and clear the entity manager
            // somehow
            final TaskFetcher taskFetcher = new TaskFetcher(em);
            Notification task3 = (Notification) taskFetcher.fetchTaskIfExists(task1
                    .getID());
           
            checkRemoved(task1);
           }
           
           @Test
           public void checkForDoubles() throws Exception {
            Task task1 = new Notification(getUniqueTaskID(), new URI(
                    "http://hellonico.net"), getXmlSampleDocument());
            task1.getRoleOwners().add("examples\\employee");
            task1.getRoleOwners().add("examples\\manager");
            persist(task1);
           
            final TaskFetcher taskFetcher = new TaskFetcher(em);
            UserRoles ur = new UserRoles("niko", new String[] {
                    "examples\\employee", "examples\\manager" });
            Task[] list = taskFetcher.fetchAllAvailableTasks(ur);
            Assert.assertEquals(1, list.length);
           }
           
           @Test
           public void notificationOneRolePersist() throws Exception {
            Notification task1 = null, task2 = null;
           
            task1 = new Notification(getUniqueTaskID(), new URI(
                    "http://hellonico.net"), getXmlSampleDocument());
            task1.getRoleOwners().add("intalio\\manager");
            persist(task1);
           
            final TaskFetcher taskFetcher = new TaskFetcher(em);
            task2 = (Notification) taskFetcher
                    .fetchTasksForRole("intalio\\manager")[0];
            TaskEquality.areTasksEquals(task1, task2);
           
            checkRemoved(task2);
           }
           
           @Test
           public void notificationOneUserPersiste() throws Exception {
            Notification task1 = null, task2 = null;
           
            task1 = new Notification(getUniqueTaskID(), new URI(
                    "http://hellonico.net"), getXmlSampleDocument());
            task1.getUserOwners().add("intalio\\admin");
            persist(task1);
           
            final TaskFetcher taskFetcher = new TaskFetcher(em);
            task2 = (Notification) taskFetcher.fetchTasksForUser("intalio\\admin")[0];
            TaskEquality.areTasksEquals(task1, task2);
           
            checkRemoved(task2);
           }
           
           @Test
           public void PIPATaskSearchFromURL() throws Exception {
            PIPATask task1 = new PIPATask(getUniqueTaskID(), new URI(
                    "http://hellonico2.net"), new URI("http://hellonico2.net"),
                    new URI("http://hellonico2.net"), "initOperationSOAPAction");
            persist(task1);
            Query q = em.createNamedQuery(PIPATask.FIND_BY_URL).setParameter(1,
                    "http://hellonico2.net");
            PIPATask task2 = (PIPATask) (q.getSingleResult());
            TaskEquality.areTasksEquals(task1, task2);
            checkRemoved(task2);
           }
           
           @Test
           public void PATaskPersistence() throws Exception {
           
            String id = getUniqueTaskID();
            PATask task1 = new PATask(id, new URI("http://hellonico.net"),
                    "processId", "soap", getXmlSampleDocument());
            task1.setDeadline(new Date());
           
            persist(task1);
           
            Query q = em.createNamedQuery(Task.FIND_BY_ID).setParameter(1, id);
            PATask task2 = (PATask) (q.getResultList()).get(0);
           
            TaskEquality.areTasksEquals(task1, task2);
           
            Assert.assertEquals(task1.getInputAsXmlString(), task2
                    .getInputAsXmlString());
            Assert.assertEquals(task1.getInputAsXmlString(), task2
                    .getInputAsXmlString());
           
            checkRemoved(task2);
           }
           
           @Test
           public void PIPATaskPersistence() throws Exception {
            String id = getUniqueTaskID();
            PIPATask task1 = new PIPATask(id, new URI("http://hellonico.net"),
                    new URI("http://hellonico.net"),
                    new URI("http://hellonico.net"), "initOperationSOAPAction");
           
            persist(task1);
           
            Query q = em.createNamedQuery(Task.FIND_BY_ID).setParameter(1, id);
           
            PIPATask task2 = (PIPATask) (q.getResultList()).get(0);
           
            TaskEquality.areTasksEquals(task1, task2);
            checkRemoved(task2);
           }
           
           /**
            * This tests that a user cannot get a task in his task list that is not
            * available to him use the <code>isAvailableTo</code> method in the
            * <code>Task</code> class.
            */
           @Test
           public void userWithStrangeLogin() throws Exception {
            String users[] = new String[] {
                    "overdating\\pdv017@es\\japanesemaster\\com",
                    "overdating\\pdv017@es.japanesemaster.com",
                    "overdating\\pdv017@es\\japanesemaster\\com ",
                    "overdating\\pdv017@es.japanesemaster.com ", "nico", "alex",
                    "assaf", "matthieu", "nico " };
            for (String user : users)
                testLogins(user, users);
           }
           
           @Test
           public void authorizeActionForUser() throws Exception {
            String id = getUniqueTaskID();
            Notification task1 = null, task2 = null;
           
            task1 = new Notification(id, new URI("http://hellonico.net"),
                    getXmlSampleDocument());
            task1.authorizeActionForUser("play", "niko");
            task1.authorizeActionForUser("go_home", "niko");
            task1.authorizeActionForUser("eat", "alex");
            task1.getRoleOwners().add("role1");
            task1.getUserOwners().add("user1");
           
            persist(task1);
           
            Query q = em.createNamedQuery(Task.FIND_BY_ID).setParameter(1, id);
            task2 = (Notification) (q.getResultList()).get(0);
            TaskEquality.areTasksEquals(task2, task1);
           
            TaskFetcher fetcher = new TaskFetcher(em);
            Assert.assertEquals(Notification.class, fetcher
                    .fetchTasksForUser("user1")[0].getClass());
            Assert.assertEquals(Notification.class, fetcher
                    .fetchTasksForRole("role1")[0].getClass());
           
            testFecthForUserRoles("user1", new String[] { "role2" }, 1);
            testFecthForUserRoles("user2", new String[] { "role1" }, 1);
            testFecthForUserRoles("user2", new String[] { "role2" }, 0);
            testFecthForUserRoles("user3", new String[] { "role3" }, 0);
           
            checkRemoved(task2);
           }
           
           @Test
           public void NotificationPersistence() throws Exception {
           
            String id = getUniqueTaskID();
            Notification task1 = new Notification(id, new URI(
                    "http://hellonico.net"), getXmlSampleDocument());
           
            persist(task1);
           
            Query q = em.createNamedQuery(Task.FIND_BY_ID).setParameter(1, id);
            Notification task2 = (Notification) (q.getResultList()).get(0);
           
            TaskEquality.areTasksEquals(task1, task2);
           
            checkRemoved(task2);
           }
           
           @Test
           public void PAWithInputOutput() throws Exception {
            PATask task2;
            PATask task1 = new PATask(getUniqueTaskID(), new URI(
                    "http://hellonico.net"), "processId", "soap",
                    getXmlSampleDocument());
            task1.setInput(xml.getXmlDocument("/pa_input.xml"));
            task1.setOutput(xml.getXmlDocument("/pa_output.xml"));
           
            task1.getUserOwners().add("intalio\\admin");
            persist(task1);
           
            final TaskFetcher taskFetcher = new TaskFetcher(em);
            final UserRoles user = new UserRoles("intalio\\admin", new String[] {
                    "examples\\manager", "examples\\employee" });
            task2 = (PATask) taskFetcher.fetchAllAvailableTasks(user)[0];
            TaskEquality.areTasksEquals(task1, task2);
           
            checkRemoved(task2);
           
           }
           
           @Test
           public void attachmentsPATask() throws Exception {
            AttachmentMetadata metadata = new AttachmentMetadata();
            Attachment att = new Attachment(metadata, new URL(
                    "http://hellonico.net"));
            String id = "pa" + System.currentTimeMillis();
            PATask task1 = new PATask(id, new URI("http://hellonico.net"),
                    "processId", "soap", getXmlSampleDocument());
            task1.addAttachment(att);
           
            persist(task1);
           
            Query q = em.createNamedQuery(Task.FIND_BY_ID).setParameter(1, id);
            PATask task2 = (PATask) (q.getResultList()).get(0);
           
            TaskEquality.areAttachmentsEqual(task1, task2);
           
            checkRemoved(task2);
           }
           
           @Test
           public void authorizeUserRolesAndIsAvailable() throws Exception {
            String id = getUniqueTaskID();
            PATask task1 = new PATask(id, new URI("http://hellonico.net"),
                    "processId", "soap", getXmlSampleDocument());
            task1.authorizeActionForUser("save", "examples\\manager");
            task1.authorizeActionForUser("save", "user1");
            task1.getUserOwners().add("user2");
            task1.getRoleOwners().add("role1");
           
            task1.setPriority(2);
            persist(task1);
           
            Query q = em.createNamedQuery(Task.FIND_BY_ID).setParameter(1, id);
            PATask task2 = (PATask) (q.getResultList()).get(0);
           
            TaskEquality.areTasksEquals(task1, task2);
           
            final UserRoles credentials = new UserRoles("user1",
                    new String[] { "role1" });
            final UserRoles credentials2 = new UserRoles("user2",
                    new String[] { "role2" });
            final UserRoles credentials3 = new UserRoles("user3",
                    new String[] { "role3" });
           
            Assert.assertTrue(task2.isAvailableTo(credentials));
            Assert.assertTrue(task2.isAvailableTo(credentials2));
            Assert.assertFalse(task2.isAvailableTo(credentials3));
           
            Assert.assertTrue(task2.isAuthorizedAction(credentials, "save"));
            Assert.assertTrue(task2.isAuthorizedAction(credentials, "cook"));
           
            checkRemoved(task2);
           }
           
           @Test
           public void testFetchAvailabeTasksWithCriteriaPA() throws Exception {
            PATask task1 = getSampleTask(TaskState.FAILED);
           
            persist(task1);
           
            testFetchForUserRolesWithCriteria("user1", new String[] { "role1" },
                    PATask.class, "T._state = TaskState.FAILED", 1);
            testFetchForUserRolesWithCriteria("user1", new String[] { "role1" },
                    PATask.class,
                    "T._state = TaskState.FAILED and T._priority = 2", 1);
            testFetchForUserRolesWithCriteria("user1", new String[] { "role1" },
                    PATask.class,
                    "T._state = TaskState.FAILED and T._priority = 1", 0);
            testFetchForUserRolesWithCriteria("user1", new String[] { "role1" },
                    PATask.class, "T._state = TaskState.FAILED or T._priority = 1",
                    1);
            testFetchForUserRolesWithCriteria("user3", new String[] { "role3" },
                    PATask.class, "T._state = TaskState.FAILED", 0);
            testFetchForUserRolesWithCriteria("user1", new String[] { "role1" },
                    PATask.class, "T._state = TaskState.COMPLETED", 0);
            testFetchForUserRolesWithCriteria(
                    "user1",
                    new String[] { "role1" },
                    PATask.class,
                    "NOT T._state = TaskState.COMPLETED and NOT T._state = TaskState.READY  AND T._description like '%%' ",
                    1);
            testFetchForUserRolesWithCriteria("user1", new String[] { "role1" },
                    Notification.class, null, 0);
           
            checkRemoved(task1.getID());
           }
           
           @Test
           public void testFetchWithCriteriaAndOrder() throws Exception {
            PATask task1 = getSampleTask(TaskState.READY);
            task1.setDescription("Ztarting with a Z");
            PATask task2 = getSampleTask(TaskState.CLAIMED);
            task2.setDescription("Arting with a A");
            PATask task3 = getSampleTask(TaskState.FAILED);
           
            persist(task1);
            persist(task2);
            persist(task3);
           
            String PA_QUERY_READY_OR_CLAIMED_ORDERED = "T._state = TaskState.READY or T._state = TaskState.CLAIMED ORDER BY t._description ASC";
            Task[] tasks = testFetchForUserRolesWithCriteria("user1",
                    new String[] { "role1" }, PATask.class,
                    PA_QUERY_READY_OR_CLAIMED_ORDERED, 2);
            // this way we can confirm the tasks are ordered
            Assert.assertTrue(tasks[0].getDescription().startsWith("A"));
           
            // check the query is working for other tasks as well
            String QUERY_READY_OR_CLAIMED_ORDERED = "ORDER BY t._creationDate ASC";
            testFetchForUserRolesWithCriteria("user1", new String[] { "role1" },
                    Task.class, QUERY_READY_OR_CLAIMED_ORDERED, 3);
            testFetchForUserRolesWithCriteria("user1", new String[] { "role1" },
                    Class.forName("org.intalio.tempo.workflow.task.Task"),
                    QUERY_READY_OR_CLAIMED_ORDERED, 3);
           
            checkRemoved(task1);
            checkRemoved(task2);
            checkRemoved(task3);
           }
           
           @Test
           public void testFetchAvailabeTasksWithCriteriaNOTI() throws Exception {
            String id = getUniqueTaskID();
            Notification task2 = new Notification(id, new URI(
                    "http://hellonico.net"), getXmlSampleDocument());
            task2.getRoleOwners().add("role1");
            task2.getUserOwners().add("user1");
           
            persist(task2);
           
            testFetchForUserRolesWithCriteria("user1", new String[] { "role1" },
                    Notification.class, null, 1);
             testFetchForUserRolesWithCriteria("user3", new String[] { "role3" },
             Notification.class, null, 0);
             testFetchForUserRolesWithCriteria("user1", new String[] { "role1" },
             PIPATask.class, null, 0);
           
            checkRemoved(task2.getID());
           }
           
           @Test
           public void testBombNotifications() throws Exception {
            // int BOMB_SIZE = 1000;
            int BOMB_SIZE = 5;
            ArrayList ids = new ArrayList(BOMB_SIZE);
            for (int i = 0; i < BOMB_SIZE; i++) {
                String id = getUniqueTaskID();
                Notification task2 = new Notification(id, new URI(
                        "http://hellonico.net"), getXmlSampleDocument());
                task2.getRoleOwners().add("role1");
                task2.getUserOwners().add("user1");
                persist(task2);
                ids.add(task2);
            }
           
            testFetchForUserRolesWithCriteria("user1", new String[] { "role1" },
                    Notification.class, null, BOMB_SIZE, true);
            // testFetchForUserRolesWithCriteria("user3", new String[] { "role3" },
            // Notification.class, null, 0);
            // testFetchForUserRolesWithCriteria("user1", new String[] { "role1" },
            // PIPATask.class, null, 0);
           
            checkRemoved(ids);
           }
           
           @Test
           public void testFetchAvailabeTasksWithCriteriaPIPA() throws Exception {
            String id = getUniqueTaskID();
            PIPATask task1 = new PIPATask(id, new URI("http://hellonico.net"),
                    new URI("http://hellonico.net"),
                    new URI("http://hellonico.net"), "initOperationSOAPAction");
           
            task1.getRoleOwners().add("role1");
            task1.getUserOwners().add("user1");
           
            persist(task1);
           
            testFetchForUserRolesWithCriteria("user1", new String[] { "role1" },
                    PIPATask.class, null, 1);
            testFetchForUserRolesWithCriteria("user3", new String[] { "role3" },
                    PIPATask.class, null, 0);
           
            checkRemoved(task1.getID());
           
           }

}
