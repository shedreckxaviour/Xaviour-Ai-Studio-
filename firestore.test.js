const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverDate() {
  return new Date(Date.now() - 1000);
}

function validProjectPayload(userId) {
  const now = serverDate();
  return {
    userId,
    title: "Black Holes Explained",
    prompt: "Explain how black holes warp spacetime",
    durationMinutes: 5,
    aspectRatio: "16:9",
    videoStyle: "Space Documentary",
    language: "English",
    narrationStyle: "Deep documentary",
    status: "draft",
    currentStageIndex: 0,
    freeModeUsed: true,
    modelUsed: "gemini-3.8-flash",
    visualSourceMode: "free_animated_slides",
    brandingEnabled: true,
    outroDurationSec: 5,
    estimatedSizeBytes: 0,
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot list or read projects", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("projects").get());
});

test("Authenticated owner: can create, read, update, and list own project", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projectRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1");
  await assertSucceeds(projectRef.set(validProjectPayload(ALICE_UID)));
  await assertSucceeds(projectRef.get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertSucceeds(projectRef.update({ title: "Black Holes Deep Dive", updatedAt: serverDate() }));
});

test("Cross-user isolation: Bob cannot read or write Alice project", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projectRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1");
  await assertSucceeds(projectRef.set(validProjectPayload(ALICE_UID)));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_2").set(validProjectPayload(ALICE_UID)));
});

test("Shadow update & identity spoofing rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projectRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_shadow");
  await assertFails(projectRef.set({ ...validProjectPayload(ALICE_UID), ghostField: true }));
  await assertFails(projectRef.set(validProjectPayload(BOB_UID)));
});

test("Scenes subcollection enforces parent existence and owner access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projectRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_scenes");
  const sceneRef = projectRef.collection("scenes").doc("scene_1");
  const now = serverDate();

  // Orphan scene fails when parent project doesn't exist
  await assertFails(sceneRef.set({
    userId: ALICE_UID,
    projectId: "proj_scenes",
    sceneIndex: 0,
    title: "Intro",
    narration: "In the dark heart of space...",
    visualPrompt: "Glowing event horizon",
    estimatedSeconds: 20,
    factType: "verified_fact",
    visualSourceType: "free_motion_slide",
    status: "pending",
    createdAt: now,
    updatedAt: now,
  }));

  await assertSucceeds(projectRef.set(validProjectPayload(ALICE_UID)));
  await assertSucceeds(sceneRef.set({
    userId: ALICE_UID,
    projectId: "proj_scenes",
    sceneIndex: 0,
    title: "Intro",
    narration: "In the dark heart of space...",
    visualPrompt: "Glowing event horizon",
    estimatedSeconds: 20,
    factType: "verified_fact",
    visualSourceType: "free_motion_slide",
    status: "pending",
    createdAt: now,
    updatedAt: now,
  }));
});

test("UsageLogs are append-only (no update) and owner-isolated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const logRef = aliceDb.collection("users").doc(ALICE_UID).collection("usageLogs").doc("log_1");
  await assertSucceeds(logRef.set({
    userId: ALICE_UID,
    projectId: "proj_1",
    modelName: "gemini-3.8-flash",
    operation: "script_generation",
    isFreeTier: true,
    estimatedTokens: 1200,
    status: "success",
    createdAt: serverDate(),
  }));
  await assertFails(logRef.update({ estimatedTokens: 0 }));
});
