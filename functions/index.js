const functions = require("firebase-functions");
const admin = require("firebase-admin");
const express = require("express");
const cors = require("cors");

admin.initializeApp();
const db = admin.firestore();

const app = express();
app.use(cors({ origin: true }));
app.use(express.json());

const DEFAULT_GOAL = 5000;

const KARMA_LEVELS = {
  VNEGATIVE: -3,
  NEGATIVE: -2,
  SNEGATIVE: -1,
  BASE: 0,
  SPOSITIVE: 1,
  POSITIVE: 2,
  VPOSITIVE: 3,
};

function karmaFromLevel(level) {
  if (level <= -3) return "VNEGATIVE";
  if (level === -2) return "NEGATIVE";
  if (level <= 0) return "SNEGATIVE";
  if (level === 1) return "SPOSITIVE";
  if (level === 2) return "POSITIVE";
  return "VPOSITIVE";
}

function calculateFromGoal(currentKarma, goalAchieved) {
  if (currentKarma === "BASE") {
    return goalAchieved ? "POSITIVE" : "NEGATIVE";
  }
  const currentLevel = KARMA_LEVELS[currentKarma] ?? 0;
  const nextLevel = goalAchieved ? currentLevel + 1 : currentLevel - 1;
  return karmaFromLevel(nextLevel);
}

function getTodayString() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/**
 * Endpoint de Login do Jogador:
 * GET /api/playerLogin?username=...&uuid=...
 */
app.get("/playerLogin", async (req, res) => {
  try {
    const username = req.query.username;
    const uuid = req.query.uuid || "";

    if (!username) {
      return res.status(400).json({ error: "Parâmetro 'username' é obrigatório." });
    }

    const playerRef = db.collection("players").document(username);
    let playerSnap = await playerRef.get();

    if (!playerSnap.exists) {
      const initialData = {
        minecraft_username: username,
        uuid: uuid,
        goal: DEFAULT_GOAL,
        karma: "BASE",
        lastProcessedVisitDate: null,
        lastProcessedGoal: null,
        karmaBeforeLastProcessedVisit: "BASE",
      };
      await playerRef.set(initialData);
      playerSnap = await playerRef.get();
    }

    const profile = playerSnap.data() || {};
    const goal = profile.goal && profile.goal > 0 ? profile.goal : DEFAULT_GOAL;
    const storedKarma = profile.karma || "BASE";
    const lastProcessedVisitDate = profile.lastProcessedVisitDate || null;
    const lastProcessedGoal = profile.lastProcessedGoal || null;

    // Consultar registos em user_visits
    const visitsSnap = await db
      .collection("user_visits")
      .where("minecraft_username", "==", username)
      .get();

    if (visitsSnap.empty) {
      return res.json({
        status: "ok",
        username,
        karma: "BASE",
        stepsOntem: 0,
        hasYesterdayData: false,
        goal,
      });
    }

    const docs = visitsSnap.docs.map((d) => d.data());
    docs.sort((a, b) => {
      const d1 = a.date || "";
      const d2 = b.date || "";
      return d2.localeCompare(d1);
    });

    const latestDoc = docs[0];
    const latestVisitDate = latestDoc.date;
    const todayStr = getTodayString();

    let processedVisit = null;
    if (latestVisitDate === todayStr) {
      if (docs.length >= 2) {
        processedVisit = docs[1];
      }
    } else {
      processedVisit = latestDoc;
    }

    if (!processedVisit || processedVisit.steps == null) {
      return res.json({
        status: "ok",
        username,
        karma: storedKarma,
        stepsOntem: 0,
        hasYesterdayData: false,
        goal,
      });
    }

    const visitDate = processedVisit.date;
    const stepsOntem = Number(processedVisit.steps);
    let currentKarma = storedKarma;

    // Atualizar se for nova data ou a meta tiver mudado
    if (visitDate !== lastProcessedVisitDate || goal !== lastProcessedGoal) {
      let baseKarma = storedKarma;
      if (visitDate === lastProcessedVisitDate) {
        baseKarma = profile.karmaBeforeLastProcessedVisit || "BASE";
      }

      const goalAchieved = stepsOntem >= goal;
      currentKarma = calculateFromGoal(baseKarma, goalAchieved);

      await playerRef.update({
        karma: currentKarma,
        lastProcessedVisitDate: visitDate,
        lastProcessedGoal: goal,
        karmaBeforeLastProcessedVisit: baseKarma,
      });
    }

    return res.json({
      status: "ok",
      username,
      karma: currentKarma,
      stepsOntem,
      hasYesterdayData: true,
      goal,
    });
  } catch (error) {
    console.error("Erro no playerLogin:", error);
    return res.status(500).json({ error: "Erro interno do servidor", details: error.message });
  }
});

/**
 * Endpoint de Consulta de Passos:
 * GET /api/steps?username=...
 */
app.get("/steps", async (req, res) => {
  try {
    const username = req.query.username;

    if (!username) {
      return res.status(400).json({ error: "Parâmetro 'username' é obrigatório." });
    }

    const visitsSnap = await db
      .collection("user_visits")
      .where("minecraft_username", "==", username)
      .get();

    if (visitsSnap.empty) {
      return res.json({
        status: "ok",
        username,
        found: false,
        totalSteps: 0,
        totalDays: 0,
      });
    }

    const docs = visitsSnap.docs.map((d) => d.data());
    docs.sort((a, b) => {
      const d1 = a.date || "";
      const d2 = b.date || "";
      return d2.localeCompare(d1);
    });

    let totalSteps = 0;
    for (const doc of docs) {
      if (doc.steps) totalSteps += Number(doc.steps);
    }

    const docHoje = docs[0];
    const stepsHoje = docHoje && docHoje.steps != null ? Number(docHoje.steps) : null;
    const dateHoje = docHoje ? docHoje.date : null;

    let stepsOntem = null;
    let dateOntem = null;
    if (docs.length >= 2) {
      const docOntem = docs[1];
      stepsOntem = docOntem && docOntem.steps != null ? Number(docOntem.steps) : null;
      dateOntem = docOntem ? docOntem.date : null;
    }

    return res.json({
      status: "ok",
      username,
      found: true,
      totalSteps,
      totalDays: docs.length,
      stepsHoje,
      dateHoje,
      stepsOntem,
      dateOntem,
    });
  } catch (error) {
    console.error("Erro em /steps:", error);
    return res.status(500).json({ error: "Erro interno do servidor", details: error.message });
  }
});

/**
 * Endpoint para definir meta de passos:
 * POST /api/setGoal
 * Body: { username: string, goal: number }
 */
app.post("/setGoal", async (req, res) => {
  try {
    const { username, goal } = req.body;

    if (!username) {
      return res.status(400).json({ error: "Parâmetro 'username' é obrigatório." });
    }

    const newGoal = Number(goal);
    if (isNaN(newGoal) || newGoal <= 0) {
      return res.status(400).json({ error: "A meta de passos deve ser um número maior que 0." });
    }

    const playerRef = db.collection("players").document(username);
    await playerRef.set(
      {
        minecraft_username: username,
        goal: newGoal,
      },
      { merge: true }
    );

    return res.json({
      status: "ok",
      username,
      goal: newGoal,
      message: `Meta de passos atualizada para ${newGoal} passos diários!`,
    });
  } catch (error) {
    console.error("Erro em /setGoal:", error);
    return res.status(500).json({ error: "Erro interno do servidor", details: error.message });
  }
});

exports.api = functions.https.onRequest(app);
