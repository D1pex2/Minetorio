local function emit(t) localised_print('RENAME '..game.table_to_json(t)) end

local function snapshot(kind, detail)
  if not global.ready then return end
  local result={kind=kind,tick=game.tick,phase=global.phase,detail=detail,trains={},stops={},counts={}}
  for _,loco in ipairs(global.locos) do
    assert(loco.valid)
    local train=loco.train
    result.trains[#result.trains+1]={id=train.id,unit_number=loco.unit_number,force=loco.force.index,
      surface=loco.surface.index,state=train.state,manual=train.manual_mode,speed=train.speed,
      fuel_count=loco.get_fuel_inventory().get_item_count(),schedule=train.schedule}
  end
  for _,stop in ipairs(global.stops) do
    assert(stop.valid)
    result.stops[#result.stops+1]={unit_number=stop.unit_number,name=stop.backer_name,
      force=stop.force.index,surface=stop.surface.index,connected=stop.connected_rail~=nil}
  end
  for _,surface in ipairs(global.surfaces) do
    local names={}
    for _,stop in pairs(surface.find_entities_filtered{type='train-stop'}) do
      names[stop.backer_name]=(names[stop.backer_name] or 0)+1
    end
    result.counts[#result.counts+1]={surface=surface.index,names=names}
  end
  emit(result)
end

local function surface(name)
  local s=game.create_surface(name,{seed=12345,water='none',starting_area='none',
    autoplace_controls={['enemy-base']={frequency='none',size='none',richness='none'}}})
  for x=0,64,32 do for y=-128,32,32 do s.request_to_generate_chunks({x,y},0) end end
  s.force_generate_chunk_requests()
  global.surfaces[#global.surfaces+1]=s
  return s
end

local function stop(s,force,x,y,name)
  local e=s.create_entity{name='train-stop',position={x+2,y},direction=defines.direction.north,force=force}
  assert(e and e.valid,'stop creation failed')
  e.backer_name=name
  assert(e.connected_rail,'stop disconnected')
  global.stops[#global.stops+1]=e
  return e
end

local function trains(s,force,x,old,shared)
  for _,lane in ipairs({x,x+16}) do
    for y=-110,30,2 do assert(s.create_entity{name='straight-rail',position={lane,y},direction=defines.direction.north,force=force}) end
  end
  for i,lane in ipairs({x,x+16}) do
    local l=s.create_entity{name='locomotive',position={lane,0},direction=defines.direction.north,force=force}
    assert(l and l.valid,'locomotive creation failed')
    local offset=i*100
    l.train.schedule={current=2,records={
      {station=old,wait_conditions={{type='time',compare_type='and',ticks=offset+11}}},
      {station='Keep',wait_conditions={{type='time',compare_type='and',ticks=offset+22},{type='inactivity',compare_type='or',ticks=offset+23}}},
      {station=old,wait_conditions={{type='time',compare_type='and',ticks=offset+33}}},
      {station=shared,wait_conditions={{type='time',compare_type='and',ticks=offset+44}}}
    }}
    l.train.manual_mode=i==1
    global.locos[#global.locos+1]=l
  end
end

local function begin_phase(n)
  global.ready=false;global.phase=n;global.locos={};global.stops={};global.surfaces={};global.actions={}
  if n==1 then
    local s=surface('rename-phase-1');local f=game.forces.player
    trains(s,f,0,'Old','Shared')
    local u=stop(s,f,16,-40,'Old')
    local d1=stop(s,f,16,-60,'Shared');local d2=stop(s,f,16,-80,'Shared')
    stop(s,f,16,-100,'Keep')
    global.actions={{entity=u,name='New'},{entity=d1,name='First'},{entity=d2,name='Last'}}
  elseif n==2 then
    local f=game.create_force('rename-phase-2')
    for i=1,2 do
      local s=surface('rename-phase-2-'..i)
      trains(s,f,0,'Across','Shared2')
      local e=stop(s,f,16,-40,'Across')
      stop(s,f,16,-60,'Keep');stop(s,f,16,-80,'Shared2')
      global.actions[i]={entity=e,name=i==1 and 'FirstAcross' or 'LastAcross'}
    end
  else
    local s=surface('rename-phase-3')
    for i=1,2 do
      local f=game.create_force('rename-phase-3-'..i);local x=(i-1)*40
      trains(s,f,x,'ForceOld','Shared3')
      local e=stop(s,f,x+16,-40,'ForceOld')
      stop(s,f,x+16,-60,'Keep');stop(s,f,x+16,-80,'Shared3')
      global.actions[i]={entity=e,name=i==1 and 'ForceFirst' or 'ForceLast'}
    end
  end
  global.phase_start=game.tick;global.ready=true
  snapshot('setup')
end

local function assert_baseline()
  local ids={}
  for i,l in ipairs(global.locos) do
    local t=l.train
    assert(not ids[t.id],'train identity collision');ids[t.id]=true
    assert(t.speed==0 and l.get_fuel_inventory().get_item_count()==0,'train moving or fuelled')
    assert(t.manual_mode==(i%2==1),'manual mode mismatch')
    assert(t.schedule.current==2 and #t.schedule.records==4,'schedule setup mismatch')
  end
  local expected=global.phase==1 and 4 or 6
  assert(#global.stops==expected,'stop count mismatch')
  local names={}
  for _,e in ipairs(global.stops) do
    assert(e.connected_rail,'baseline disconnected stop')
    names[e.backer_name]=(names[e.backer_name] or 0)+1
  end
  if global.phase==1 then assert(names.Old==1 and names.Shared==2 and names.Keep==1,'phase 1 names mismatch')
  elseif global.phase==2 then assert(names.Across==2 and names.Shared2==2 and names.Keep==2,'phase 2 names mismatch')
  else assert(names.ForceOld==2 and names.Shared3==2 and names.Keep==2,'phase 3 names mismatch') end
  snapshot('baseline_asserted',{train_count=#global.locos,stop_count=#global.stops})
end

script.on_init(function() global.ready=false;emit({kind='init',version=game.active_mods.base}) end)
script.on_event(defines.events.on_entity_renamed,function(event)
  snapshot('on_entity_renamed',{old_name=event.old_name,new_name=event.entity.backer_name,
    by_script=event.by_script,player_index=event.player_index,unit_number=event.entity.unit_number})
end)
script.on_event(defines.events.on_train_schedule_changed,function(event)
  snapshot('on_train_schedule_changed',{train_id=event.train.id,player_index=event.player_index})
end)
script.on_event(defines.events.on_tick,function(event)
  if event.tick==0 then begin_phase(1) end
  if not global.ready then return end
  local elapsed=event.tick-global.phase_start
  if elapsed==2 then assert_baseline() end
  for i,action in ipairs(global.actions) do
    local action_tick=4+(i-1)*4
    if elapsed==action_tick then
      snapshot('before_setter',{action=i})
      action.entity.backer_name=action.name
      snapshot('immediate_after_setter',{action=i})
    elseif elapsed==action_tick+1 then snapshot('next_tick',{action=i})
    elseif elapsed==action_tick+2 then snapshot('second_tick',{action=i}) end
  end
  if elapsed==17 then
    snapshot('phase_complete')
    if global.phase<3 then begin_phase(global.phase+1)
    else global.ready=false;emit({kind='complete',tick=game.tick}) end
  end
end)
