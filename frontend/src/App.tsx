import { Route, Routes } from 'react-router-dom';
import { AppShell, MinimalShell } from './layout/AppShell';
import { LoginPage } from './pages/auth/LoginPage';
import { RegisterPage } from './pages/auth/RegisterPage';
import { BattlePage } from './pages/battle/BattlePage';
import { CatalogDetailPage } from './pages/catalog/CatalogDetailPage';
import { CatalogPage } from './pages/catalog/CatalogPage';
import { CollectionDetailPage } from './pages/collection/CollectionDetailPage';
import { CollectionPage } from './pages/collection/CollectionPage';
import { DashboardPage } from './pages/dashboard/DashboardPage';
import { EncountersPage } from './pages/encounters/EncountersPage';
import { HistoryDetailPage } from './pages/history/HistoryDetailPage';
import { HistoryPage } from './pages/history/HistoryPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { RunDetailPage } from './pages/runs/RunDetailPage';
import { RunsPage } from './pages/runs/RunsPage';
import { StartersPage } from './pages/starters/StartersPage';
import { TeamEditorPage } from './pages/teams/TeamEditorPage';
import { TeamsPage } from './pages/teams/TeamsPage';
import { GuestOnly, RequireAuth } from './session/guards';

/** Routes of docs/plan/09-frontend.md. */
export function App() {
  return (
    <Routes>
      <Route element={<GuestOnly />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
      </Route>

      <Route element={<RequireAuth />}>
        <Route element={<MinimalShell />}>
          <Route path="/starters" element={<StartersPage />} />
        </Route>
        <Route element={<AppShell />}>
          <Route index element={<DashboardPage />} />
          <Route path="/catalog" element={<CatalogPage />} />
          <Route path="/catalog/:id" element={<CatalogDetailPage />} />
          <Route path="/collection" element={<CollectionPage />} />
          <Route path="/collection/:ownedId" element={<CollectionDetailPage />} />
          <Route path="/teams" element={<TeamsPage />} />
          <Route path="/teams/new" element={<TeamEditorPage />} />
          <Route path="/teams/:id" element={<TeamEditorPage />} />
          <Route path="/encounters" element={<EncountersPage />} />
          <Route path="/battle" element={<BattlePage />} />
          <Route path="/runs" element={<RunsPage />} />
          <Route path="/runs/:id" element={<RunDetailPage />} />
          <Route path="/history" element={<HistoryPage />} />
          <Route path="/history/:id" element={<HistoryDetailPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
